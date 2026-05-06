package com.co.eatupapi.services.payment.invoice;

import com.co.eatupapi.domain.payment.invoice.Invoice;
import com.co.eatupapi.domain.payment.invoice.InvoiceDetail;
import com.co.eatupapi.domain.payment.invoice.InvoiceStatus;
import com.co.eatupapi.messaging.payment.invoice.InvoiceCancelMessage;
import com.co.eatupapi.messaging.payment.invoice.InvoiceCreateMessage;
import com.co.eatupapi.messaging.payment.invoice.InvoiceItemMessage;
import com.co.eatupapi.messaging.payment.invoice.InvoiceMarkPaidMessage;
import com.co.eatupapi.repositories.payment.invoice.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class InvoiceCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceCommandHandler.class);

    private static final Set<InvoiceStatus> INACTIVE_STATUSES = Set.of(
            InvoiceStatus.CANCELLED,
            InvoiceStatus.VOIDED
    );

    private final InvoiceRepository invoiceRepository;

    public InvoiceCommandHandler(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    // ──────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────

    @Transactional
    public void handleCreate(InvoiceCreateMessage message) {
        validateCreateMessage(message);

        LocalDateTime effectiveDate = resolveDate(message.getInvoiceDate(), message.getEventDate());

        // Idempotency: check if invoice with this ID already exists
        Optional<Invoice> existingById = invoiceRepository.findById(message.getInvoiceId());
        if (existingById.isPresent()) {
            Invoice existing = existingById.get();
            if (matchesPrimaryData(existing, message)) {
                log.info("Invoice already exists with matching data, skipping create: id={}", message.getInvoiceId());
                return;
            }
            throw new InvoiceProcessingException(
                    "Invoice already exists with different data: id=" + message.getInvoiceId()
            );
        }

        // Check duplicate invoiceNumber + locationId
        if (invoiceRepository.existsByInvoiceNumberAndLocationId(message.getInvoiceNumber(), message.getLocationId())) {
            throw new InvoiceProcessingException(
                    "Invoice already exists with invoiceNumber=" + message.getInvoiceNumber()
                            + " and locationId=" + message.getLocationId()
            );
        }

        // Check active invoice for same salesId + locationId
        if (message.getSalesId() != null &&
                invoiceRepository.existsBySalesIdAndLocationIdAndStatusNotIn(
                        message.getSalesId(), message.getLocationId(), INACTIVE_STATUSES)) {
            throw new InvoiceProcessingException(
                    "Active invoice already exists for salesId=" + message.getSalesId()
                            + " and locationId=" + message.getLocationId()
            );
        }

        // Build entity
        Invoice invoice = new Invoice();
        invoice.setId(message.getInvoiceId());
        invoice.setInvoiceNumber(message.getInvoiceNumber());
        invoice.setStatus(message.getStatus() != null ? message.getStatus() : InvoiceStatus.OPEN);
        invoice.setInvoiceDate(effectiveDate);
        invoice.setSalesId(message.getSalesId());
        invoice.setCustomerDiscountId(message.getCustomerDiscountId());
        invoice.setLocationId(message.getLocationId());
        invoice.setDiscountId(message.getDiscountId());
        invoice.setTableId(message.getTableId());
        invoice.setLocationName(message.getLocationName());
        invoice.setCustomerId(message.getCustomerId());
        invoice.setDiscountPercentage(message.getDiscountPercentage());
        invoice.setDiscountDescription(message.getDiscountDescription());
        invoice.setSubtotal(message.getSubtotal());
        invoice.setDiscountAmount(defaultZero(message.getDiscountAmount()));
        invoice.setTaxAmount(defaultZero(message.getTaxAmount()));
        invoice.setTotalPrice(message.getTotalPrice());

        // Build details
        List<InvoiceItemMessage> items = message.getDetails() != null
                ? message.getDetails()
                : Collections.emptyList();

        for (InvoiceItemMessage item : items) {
            InvoiceDetail detail = new InvoiceDetail();
            detail.setRecipeId(item.getRecipeId());
            detail.setItemName(item.getItemName());
            detail.setQuantity(item.getQuantity());
            detail.setUnitPrice(item.getUnitPrice());
            detail.setSubtotal(item.getSubtotal());
            detail.setDiscountAmount(item.getDiscountAmount());
            detail.setTaxAmount(item.getTaxAmount());
            detail.setTotal(item.getTotal());
            detail.setComment(item.getComment());
            invoice.addDetail(detail);
        }

        invoiceRepository.save(invoice);
        log.info("Invoice created: id={}, invoiceNumber={}, locationId={}",
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getLocationId());
    }

    // ──────────────────────────────────────────────
    // CANCEL
    // ──────────────────────────────────────────────

    @Transactional
    public void handleCancel(InvoiceCancelMessage message) {
        validateCancelMessage(message);

        Invoice invoice = invoiceRepository.findById(message.getInvoiceId())
                .orElseThrow(() -> new InvoiceProcessingException(
                        "Invoice not found: " + message.getInvoiceId()));

        if (!invoice.getLocationId().equals(message.getLocationId())) {
            throw new InvoiceProcessingException(
                    "Invoice does not belong to location: " + message.getLocationId());
        }

        // Idempotency: already cancelled
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            log.info("Invoice already cancelled, skipping: id={}", message.getInvoiceId());
            return;
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvoiceProcessingException(
                    "Cannot cancel a paid invoice without payment reversal: id=" + message.getInvoiceId());
        }

        if (invoice.getStatus() == InvoiceStatus.CLOSED) {
            throw new InvoiceProcessingException(
                    "Cannot cancel a closed invoice: id=" + message.getInvoiceId());
        }

        String reason = message.getReason() != null ? message.getReason() : "Cancelación solicitada";
        LocalDateTime eventDate = message.getEventDate() != null ? message.getEventDate() : LocalDateTime.now();

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(eventDate);
        invoice.setCancelReason(reason);
        invoiceRepository.save(invoice);

        log.info("Invoice cancelled: id={}, reason={}", invoice.getId(), reason);
    }

    // ──────────────────────────────────────────────
    // MARK PAID
    // ──────────────────────────────────────────────

    @Transactional
    public void handleMarkPaid(InvoiceMarkPaidMessage message) {
        validateMarkPaidMessage(message);

        Invoice invoice = invoiceRepository.findById(message.getInvoiceId())
                .orElseThrow(() -> new InvoiceProcessingException(
                        "Invoice not found: " + message.getInvoiceId()));

        if (!invoice.getLocationId().equals(message.getLocationId())) {
            throw new InvoiceProcessingException(
                    "Invoice does not belong to location: " + message.getLocationId());
        }

        if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.VOIDED) {
            throw new InvoiceProcessingException(
                    "Cannot mark as paid a " + invoice.getStatus() + " invoice: id=" + message.getInvoiceId());
        }

        // Idempotency: already paid
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            log.info("Invoice already paid, skipping: id={}", message.getInvoiceId());
            return;
        }

        LocalDateTime eventDate = message.getEventDate() != null ? message.getEventDate() : LocalDateTime.now();

        // Determine status based on paidAmount vs totalPrice
        if (message.getPaidAmount() != null && invoice.getTotalPrice() != null
                && message.getPaidAmount().compareTo(invoice.getTotalPrice()) < 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PAID);
        }

        if (message.getCashReceiptId() != null) {
            invoice.setCashReceiptId(message.getCashReceiptId());
        }
        invoice.setPaidAt(eventDate);
        invoiceRepository.save(invoice);

        log.info("Invoice marked as {}: id={}, cashReceiptId={}",
                invoice.getStatus(), invoice.getId(), message.getCashReceiptId());
    }

    // ──────────────────────────────────────────────
    // VALIDATIONS
    // ──────────────────────────────────────────────

    private void validateCreateMessage(InvoiceCreateMessage message) {
        if (message == null) {
            throw new InvoiceMessageValidationException("Create message is null");
        }
        requireUuid(message.getInvoiceId(), "invoiceId");
        requireNotBlank(message.getInvoiceNumber(), "invoiceNumber");
        requireUuid(message.getLocationId(), "locationId");
        requireUuid(message.getSalesId(), "salesId");

        if (message.getTotalPrice() == null) {
            throw new InvoiceMessageValidationException("Required field is missing: totalPrice");
        }
        if (message.getTotalPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvoiceMessageValidationException("totalPrice must be >= 0");
        }
        if (message.getSubtotal() == null) {
            throw new InvoiceMessageValidationException("Required field is missing: subtotal");
        }
        if (message.getSubtotal().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvoiceMessageValidationException("subtotal must be >= 0");
        }
    }

    private void validateCancelMessage(InvoiceCancelMessage message) {
        if (message == null) {
            throw new InvoiceMessageValidationException("Cancel message is null");
        }
        requireUuid(message.getInvoiceId(), "invoiceId");
        requireUuid(message.getLocationId(), "locationId");
    }

    private void validateMarkPaidMessage(InvoiceMarkPaidMessage message) {
        if (message == null) {
            throw new InvoiceMessageValidationException("MarkPaid message is null");
        }
        requireUuid(message.getInvoiceId(), "invoiceId");
        requireUuid(message.getLocationId(), "locationId");

        if (message.getPaidAmount() != null && message.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceMessageValidationException("paidAmount must be > 0 when provided");
        }
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    private void requireUuid(UUID value, String fieldName) {
        if (Objects.isNull(value)) {
            throw new InvoiceMessageValidationException("Required field is missing: " + fieldName);
        }
    }

    private void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvoiceMessageValidationException("Required field is missing or blank: " + fieldName);
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private LocalDateTime resolveDate(LocalDateTime primary, LocalDateTime fallback) {
        if (primary != null) return primary;
        if (fallback != null) return fallback;
        return LocalDateTime.now();
    }

    private boolean matchesPrimaryData(Invoice existing, InvoiceCreateMessage message) {
        return Objects.equals(existing.getInvoiceNumber(), message.getInvoiceNumber())
                && Objects.equals(existing.getLocationId(), message.getLocationId())
                && Objects.equals(existing.getSalesId(), message.getSalesId())
                && existing.getTotalPrice() != null
                && existing.getTotalPrice().compareTo(message.getTotalPrice()) == 0;
    }
}
