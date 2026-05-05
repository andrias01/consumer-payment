package com.co.eatupapi.services.payment.cashreceipt;

import com.co.eatupapi.domain.payment.cashreceipt.CashReceipt;
import com.co.eatupapi.domain.payment.cashreceipt.CashReceiptStatus;
import com.co.eatupapi.messaging.payment.cashreceipt.CashReceiptCancelMessage;
import com.co.eatupapi.messaging.payment.cashreceipt.CashReceiptCreateMessage;
import com.co.eatupapi.repositories.payment.cashreceipt.CashReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CashReceiptCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CashReceiptCommandHandler.class);
    private static final Set<String> PAYABLE_STATUSES = Set.of("OPEN", "PENDING", "PARTIALLY_PAID");

    private final CashReceiptRepository cashReceiptRepository;

    public CashReceiptCommandHandler(CashReceiptRepository cashReceiptRepository) {
        this.cashReceiptRepository = cashReceiptRepository;
    }

    @Transactional
    public void handleCreate(CashReceiptCreateMessage message) {
        validateCreateMessage(message);
        validateInvoiceBusinessRules(message);

        BigDecimal currentPaid = sumActivePaidAmountByInvoice(message.getInvoiceId());
        BigDecimal pendingBalance = message.getInvoiceTotal().subtract(currentPaid);
        if (message.getAmount().compareTo(pendingBalance) > 0) {
            throw new IllegalArgumentException("Amount exceeds pending balance for invoice: " + message.getInvoiceId());
        }

        CashReceipt receipt = new CashReceipt();
        receipt.setLocationId(message.getLocationId());
        receipt.setInvoiceId(message.getInvoiceId());
        receipt.setAmount(message.getAmount());
        receipt.setPaymentMethodId(message.getPaymentMethodId());
        receipt.setStatus(CashReceiptStatus.PAID);
        receipt.setCreatedAt(message.getEventDate() != null ? message.getEventDate() : LocalDateTime.now());
        cashReceiptRepository.save(receipt);

        recalculateAndLogInvoiceState(message.getInvoiceId(), message.getInvoiceTotal());
    }

    @Transactional
    public void handleCancel(CashReceiptCancelMessage message) {
        validateCancelMessage(message);

        CashReceipt receipt = cashReceiptRepository.findById(message.getReceiptId())
                .orElseThrow(() -> new IllegalArgumentException("Cash receipt not found: " + message.getReceiptId()));

        if (!receipt.getLocationId().equals(message.getLocationId())) {
            throw new IllegalArgumentException("Cash receipt does not belong to location: " + message.getLocationId());
        }

        if (receipt.getStatus() == CashReceiptStatus.CANCELLED) {
            return;
        }

        receipt.setStatus(CashReceiptStatus.CANCELLED);
        receipt.setCancelledAt(message.getEventDate() != null ? message.getEventDate() : LocalDateTime.now());
        cashReceiptRepository.save(receipt);

        if (message.getInvoiceTotal() != null) {
            recalculateAndLogInvoiceState(receipt.getInvoiceId(), message.getInvoiceTotal());
        } else {
            log.warn("Skipped invoice state recalculation for cancelled receipt {} because invoiceTotal is missing",
                    message.getReceiptId());
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal sumActivePaidAmountByInvoice(UUID invoiceId) {
        validateRequiredUuid(invoiceId, "invoiceId");
        return cashReceiptRepository.sumByInvoiceAndStatus(invoiceId, CashReceiptStatus.PAID);
    }

    @Transactional(readOnly = true)
    public List<CashReceipt> getActiveReceiptsByInvoice(UUID invoiceId) {
        validateRequiredUuid(invoiceId, "invoiceId");
        return cashReceiptRepository.findByInvoiceIdAndStatus(invoiceId, CashReceiptStatus.PAID);
    }

    private void validateCreateMessage(CashReceiptCreateMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Create message is null");
        }
        validateRequiredUuid(message.getLocationId(), "locationId");
        validateRequiredUuid(message.getInvoiceId(), "invoiceId");
        validateRequiredUuid(message.getInvoiceLocationId(), "invoiceLocationId");
        validateRequiredUuid(message.getPaymentMethodId(), "paymentMethodId");
        validateRequiredValue(message.getInvoiceStatus(), "invoiceStatus");
        validateRequiredValue(message.getPaymentMethodActive(), "paymentMethodActive");
        validateRequiredAmount(message.getInvoiceTotal(), "invoiceTotal");
        validatePositiveAmount(message.getAmount());
    }

    private void validateCancelMessage(CashReceiptCancelMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Cancel message is null");
        }
        validateRequiredUuid(message.getLocationId(), "locationId");
        validateRequiredUuid(message.getReceiptId(), "receiptId");
    }

    private void validateInvoiceBusinessRules(CashReceiptCreateMessage message) {
        if (!message.getLocationId().equals(message.getInvoiceLocationId())) {
            throw new IllegalArgumentException("Invoice does not belong to location: " + message.getLocationId());
        }

        String normalizedStatus = message.getInvoiceStatus().trim().toUpperCase(Locale.ROOT);
        if (!PAYABLE_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invoice status is not payable: " + message.getInvoiceStatus());
        }

        if (!Boolean.TRUE.equals(message.getPaymentMethodActive())) {
            throw new IllegalArgumentException("Payment method is inactive: " + message.getPaymentMethodId());
        }
    }

    private void validateRequiredUuid(UUID value, String fieldName) {
        if (Objects.isNull(value)) {
            throw new IllegalArgumentException("Required field is missing: " + fieldName);
        }
    }

    private void validateRequiredValue(Object value, String fieldName) {
        if (Objects.isNull(value)) {
            throw new IllegalArgumentException("Required field is missing: " + fieldName);
        }
    }

    private void validateRequiredAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException("Required field is missing: " + fieldName);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Required field is missing: amount");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void recalculateAndLogInvoiceState(UUID invoiceId, BigDecimal invoiceTotal) {
        BigDecimal totalPaid = sumActivePaidAmountByInvoice(invoiceId);
        BigDecimal pendingBalance = invoiceTotal.subtract(totalPaid);

        String recalculatedStatus;
        if (totalPaid.compareTo(BigDecimal.ZERO) <= 0) {
            recalculatedStatus = "PENDING";
        } else if (pendingBalance.compareTo(BigDecimal.ZERO) > 0) {
            recalculatedStatus = "PARTIALLY_PAID";
        } else {
            recalculatedStatus = "PAID";
        }

        log.info(
                "Invoice recalculated after receipt operation: invoiceId={}, totalPaid={}, pendingBalance={}, status={}",
                invoiceId,
                totalPaid,
                pendingBalance.max(BigDecimal.ZERO),
                recalculatedStatus
        );
    }
}
