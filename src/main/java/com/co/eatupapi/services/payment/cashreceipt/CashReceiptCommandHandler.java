package com.co.eatupapi.services.payment.cashreceipt;

import com.co.eatupapi.domain.payment.cashreceipt.CashReceipt;
import com.co.eatupapi.domain.payment.cashreceipt.CashReceiptStatus;
import com.co.eatupapi.messaging.payment.cashreceipt.CashReceiptCancelMessage;
import com.co.eatupapi.messaging.payment.cashreceipt.CashReceiptCreateMessage;
import com.co.eatupapi.repositories.payment.cashreceipt.CashReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class CashReceiptCommandHandler {

    private final CashReceiptRepository cashReceiptRepository;

    public CashReceiptCommandHandler(CashReceiptRepository cashReceiptRepository) {
        this.cashReceiptRepository = cashReceiptRepository;
    }

    @Transactional
    public void handleCreate(CashReceiptCreateMessage message) {
        validateCreateMessage(message);

        CashReceipt receipt = new CashReceipt();
        receipt.setLocationId(message.getLocationId());
        receipt.setInvoiceId(message.getInvoiceId());
        receipt.setAmount(message.getAmount());
        receipt.setPaymentMethodId(message.getPaymentMethodId());
        receipt.setStatus(CashReceiptStatus.PAID);
        receipt.setCreatedAt(message.getEventDate() != null ? message.getEventDate() : LocalDateTime.now());
        cashReceiptRepository.save(receipt);
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
    }

    private void validateCreateMessage(CashReceiptCreateMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Create message is null");
        }
        validateRequiredUuid(message.getLocationId(), "locationId");
        validateRequiredUuid(message.getInvoiceId(), "invoiceId");
        validateRequiredUuid(message.getPaymentMethodId(), "paymentMethodId");
        validatePositiveAmount(message.getAmount());
    }

    private void validateCancelMessage(CashReceiptCancelMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Cancel message is null");
        }
        validateRequiredUuid(message.getLocationId(), "locationId");
        validateRequiredUuid(message.getReceiptId(), "receiptId");
    }

    private void validateRequiredUuid(UUID value, String fieldName) {
        if (Objects.isNull(value)) {
            throw new IllegalArgumentException("Required field is missing: " + fieldName);
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
}
