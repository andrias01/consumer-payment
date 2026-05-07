package com.co.eatupapi.services.payment.cashreceipt;

import com.co.eatupapi.domain.payment.cashreceipt.CashReceipt;
import com.co.eatupapi.domain.payment.cashreceipt.CashReceiptStatus;
import com.co.eatupapi.domain.payment.invoice.Invoice;
import com.co.eatupapi.domain.payment.invoice.InvoiceStatus;
import com.co.eatupapi.messaging.payment.cashreceipt.CashReceiptCancelMessage;
import com.co.eatupapi.messaging.payment.cashreceipt.CashReceiptCreateMessage;
import com.co.eatupapi.repositories.payment.cashreceipt.CashReceiptRepository;
import com.co.eatupapi.repositories.payment.invoice.InvoiceRepository;
import com.co.eatupapi.services.payment.invoice.InvoiceCommandHandler;
import com.co.eatupapi.services.payment.paymentmethod.PaymentMethodValidator;
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
    private final InvoiceRepository invoiceRepository;
    private final InvoiceCommandHandler invoiceCommandHandler;
    private final PaymentMethodValidator paymentMethodValidator;

    public CashReceiptCommandHandler(CashReceiptRepository cashReceiptRepository,
                                     InvoiceRepository invoiceRepository,
                                     InvoiceCommandHandler invoiceCommandHandler,
                                     PaymentMethodValidator paymentMethodValidator) {
        this.cashReceiptRepository = cashReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceCommandHandler = invoiceCommandHandler;
        this.paymentMethodValidator = paymentMethodValidator;
    }

    // ──────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────

    @Transactional
    public void handleCreate(CashReceiptCreateMessage message) {
        validateCreateMessage(message);
        validateInvoiceBusinessRules(message);

        // 1. Calcular saldo pendiente y evitar sobrepago
        BigDecimal currentPaid = sumActivePaidAmountByInvoice(message.getInvoiceId());
        BigDecimal pendingBalance = message.getInvoiceTotal().subtract(currentPaid);

        if (message.getAmount().compareTo(pendingBalance) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds pending balance for invoice: " + message.getInvoiceId()
                    + " | pendingBalance=" + pendingBalance
                    + " | requestedAmount=" + message.getAmount());
        }

        // 2. Crear y persistir recibo
        CashReceipt receipt = new CashReceipt();
        receipt.setLocationId(message.getLocationId());
        receipt.setInvoiceId(message.getInvoiceId());
        receipt.setAmount(message.getAmount());
        receipt.setPaymentMethodId(message.getPaymentMethodId());
        receipt.setStatus(CashReceiptStatus.PAID);
        receipt.setCreatedAt(message.getEventDate() != null ? message.getEventDate() : LocalDateTime.now());
        cashReceiptRepository.save(receipt);

        log.info("CashReceipt created: id={}, invoiceId={}, amount={}",
                receipt.getId(), receipt.getInvoiceId(), receipt.getAmount());

        // 3. Recalcular y actualizar estado real de la factura en BD
        recalculateAndUpdateInvoiceState(message.getInvoiceId(), message.getInvoiceTotal(),
                message.getLocationId());
    }

    // ──────────────────────────────────────────────
    // CANCEL
    // ──────────────────────────────────────────────

    @Transactional
    public void handleCancel(CashReceiptCancelMessage message) {
        validateCancelMessage(message);

        CashReceipt receipt = cashReceiptRepository.findById(message.getReceiptId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cash receipt not found: " + message.getReceiptId()));

        if (!receipt.getLocationId().equals(message.getLocationId())) {
            throw new IllegalArgumentException(
                    "Cash receipt does not belong to location: " + message.getLocationId());
        }

        // Idempotencia: ya estaba cancelado
        if (receipt.getStatus() == CashReceiptStatus.CANCELLED) {
            log.info("Cash receipt already cancelled, skipping: id={}", message.getReceiptId());
            return;
        }

        UUID invoiceId = receipt.getInvoiceId();

        receipt.setStatus(CashReceiptStatus.CANCELLED);
        receipt.setCancelledAt(message.getEventDate() != null ? message.getEventDate() : LocalDateTime.now());
        cashReceiptRepository.save(receipt);

        log.info("CashReceipt cancelled: id={}, invoiceId={}", message.getReceiptId(), invoiceId);

        // Recalcular y actualizar estado real de la factura
        if (message.getInvoiceTotal() != null) {
            recalculateAndUpdateInvoiceState(invoiceId, message.getInvoiceTotal(),
                    message.getLocationId());
        } else {
            log.warn("Skipped invoice state recalculation for cancelled receipt {} " +
                    "because invoiceTotal is missing", message.getReceiptId());
        }
    }

    // ──────────────────────────────────────────────
    // CONTRATOS DE CONSULTA INTERNOS
    // ──────────────────────────────────────────────

    /**
     * Suma los importes de recibos activos (PAID) de una factura.
     */
    @Transactional(readOnly = true)
    public BigDecimal sumActivePaidAmountByInvoice(UUID invoiceId) {
        validateRequiredUuid(invoiceId, "invoiceId");
        return cashReceiptRepository.sumByInvoiceAndStatus(invoiceId, CashReceiptStatus.PAID);
    }

    /**
     * Lista los recibos activos (PAID) de una factura.
     */
    @Transactional(readOnly = true)
    public List<CashReceipt> getActiveReceiptsByInvoice(UUID invoiceId) {
        validateRequiredUuid(invoiceId, "invoiceId");
        return cashReceiptRepository.findByInvoiceIdAndStatus(invoiceId, CashReceiptStatus.PAID);
    }

    // ──────────────────────────────────────────────
    // LÓGICA DE RECÁLCULO Y ACTUALIZACIÓN DE FACTURA
    // ──────────────────────────────────────────────

    /**
     * Recalcula el totalPagado, determina el nuevo estado de la factura y lo persiste en BD.
     * Si la factura queda en PAID, delega a {@link InvoiceCommandHandler#handleInvoicePaidSideEffects}
     * para ejecutar los side effects obligatorios (cierre de mesa, etc.).
     *
     * @param invoiceId    id de la factura
     * @param invoiceTotal total original de la factura
     * @param locationId   sede de la operación
     */
    private void recalculateAndUpdateInvoiceState(UUID invoiceId,
                                                  BigDecimal invoiceTotal,
                                                  UUID locationId) {
        BigDecimal totalPaid = sumActivePaidAmountByInvoice(invoiceId);
        BigDecimal pendingBalance = invoiceTotal.subtract(totalPaid);

        InvoiceStatus newStatus;
        if (totalPaid.compareTo(BigDecimal.ZERO) <= 0) {
            newStatus = InvoiceStatus.PENDING;
        } else if (pendingBalance.compareTo(BigDecimal.ZERO) > 0) {
            newStatus = InvoiceStatus.PARTIALLY_PAID;
        } else {
            newStatus = InvoiceStatus.PAID;
        }

        log.info("Invoice recalculated: invoiceId={}, totalPaid={}, pendingBalance={}, newStatus={}",
                invoiceId,
                totalPaid,
                pendingBalance.max(BigDecimal.ZERO),
                newStatus);

        // Actualizar estado en BD sólo si la factura existe y el estado es diferente
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            log.warn("Invoice not found for state update: invoiceId={}", invoiceId);
            return;
        }

        if (invoice.getStatus() == newStatus) {
            log.debug("Invoice status unchanged ({}), skipping update: invoiceId={}", newStatus, invoiceId);
        } else {
            invoice.setStatus(newStatus);
            if (newStatus == InvoiceStatus.PAID) {
                invoice.setPaidAt(LocalDateTime.now());
            }
            invoiceRepository.save(invoice);
            log.info("Invoice status updated to {}: invoiceId={}", newStatus, invoiceId);
        }

        // Si la factura quedó en PAID → ejecutar side effects obligatorios
        if (newStatus == InvoiceStatus.PAID) {
            invoiceCommandHandler.handleInvoicePaidSideEffects(invoiceId, locationId);
        }
    }

    // ──────────────────────────────────────────────
    // VALIDACIONES
    // ──────────────────────────────────────────────

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
        // Regla 1: Factura pertenece a la misma sede
        if (!message.getLocationId().equals(message.getInvoiceLocationId())) {
            throw new IllegalArgumentException(
                    "Invoice does not belong to location: " + message.getLocationId());
        }

        // Regla 2: Estado de factura pagable
        String normalizedStatus = message.getInvoiceStatus().trim().toUpperCase(Locale.ROOT);
        if (!PAYABLE_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Invoice status is not payable: " + message.getInvoiceStatus());
        }

        // Regla 3: Método de pago activo — delegado al contrato fuerte de PaymentMethodValidator
        paymentMethodValidator.validateActivePaymentMethod(
                message.getPaymentMethodId(),
                message.getPaymentMethodActive());
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
}
