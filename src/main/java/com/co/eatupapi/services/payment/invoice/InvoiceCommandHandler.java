package com.co.eatupapi.services.payment.invoice;

import com.co.eatupapi.domain.payment.invoice.Invoice;
import com.co.eatupapi.domain.payment.invoice.InvoiceDetail;
import com.co.eatupapi.domain.payment.invoice.InvoiceStatus;
import com.co.eatupapi.messaging.payment.invoice.InvoiceCancelMessage;
import com.co.eatupapi.messaging.payment.invoice.InvoiceCreateMessage;
import com.co.eatupapi.messaging.payment.invoice.InvoiceItemMessage;
import com.co.eatupapi.messaging.payment.invoice.InvoiceMarkPaidMessage;
import com.co.eatupapi.messaging.payment.invoice.InvoiceStatusUpdateMessage;
import com.co.eatupapi.repositories.payment.invoice.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
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
    private final InvoiceStateValidator invoiceStateValidator;

    public InvoiceCommandHandler(InvoiceRepository invoiceRepository,
                                 InvoiceStateValidator invoiceStateValidator) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceStateValidator = invoiceStateValidator;
    }

    @Transactional
    public void handleCreate(InvoiceCreateMessage message) {
        validateCreateMessage(message);

        LocalDateTime effectiveDate = resolveDate(message.getInvoiceDate(), message.getEventDate());

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

        if (invoiceRepository.existsByInvoiceNumberAndLocationId(message.getInvoiceNumber(), message.getLocationId())) {
            throw new InvoiceProcessingException(
                    "Invoice already exists with invoiceNumber=" + message.getInvoiceNumber()
                            + " and locationId=" + message.getLocationId()
            );
        }

        if (invoiceRepository.existsBySalesIdAndLocationIdAndStatusNotIn(
                message.getSalesId(), message.getLocationId(), INACTIVE_STATUSES)) {
            throw new InvoiceProcessingException(
                    "Active invoice already exists for salesId=" + message.getSalesId()
                            + " and locationId=" + message.getLocationId()
            );
        }

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
        invoice.setTableSessionId(message.getTableSessionId());
        invoice.setLocationName(message.getLocationName());
        invoice.setCustomerId(message.getCustomerId());
        invoice.setDiscountPercentage(message.getDiscountPercentage());
        invoice.setDiscountDescription(message.getDiscountDescription());
        invoice.setSubtotal(message.getSubtotal());
        invoice.setDiscountAmount(defaultZero(message.getDiscountAmount()));
        invoice.setTaxAmount(defaultZero(message.getTaxAmount()));
        invoice.setTotalPrice(message.getTotalPrice());

        for (InvoiceItemMessage item : message.getDetails()) {
            InvoiceDetail detail = new InvoiceDetail();
            detail.setRecipeId(item.getRecipeId());
            detail.setItemName(item.getItemName());
            detail.setQuantity(item.getQuantity());
            detail.setUnitPrice(item.getUnitPrice());
            detail.setSubtotal(item.getSubtotal());
            detail.setDiscountAmount(defaultZero(item.getDiscountAmount()));
            detail.setTaxAmount(defaultZero(item.getTaxAmount()));
            detail.setTotal(item.getTotal());
            detail.setComment(item.getComment());
            invoice.addDetail(detail);
        }

        invoiceRepository.save(invoice);
        log.info("Invoice created: id={}, invoiceNumber={}, locationId={}",
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getLocationId());
    }

    @Transactional
    public void handleCancel(InvoiceCancelMessage message) {
        validateCancelMessage(message);

        Invoice invoice = invoiceRepository.findById(message.getInvoiceId())
                .orElseThrow(() -> new InvoiceProcessingException(
                        "Invoice not found: " + message.getInvoiceId()
                ));

        validateInvoiceOwnership(invoice, message.getLocationId());

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            log.info("Invoice already cancelled, skipping: id={}", message.getInvoiceId());
            return;
        }

        invoiceStateValidator.validateTransition(invoice.getStatus(), InvoiceStatus.CANCELLED);

        LocalDateTime eventDate = resolveDate(message.getEventDate(), null);
        String reason = defaultReason(message.getReason(), "Cancellation requested");

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(eventDate);
        invoice.setCancelReason(reason);
        invoiceRepository.save(invoice);

        log.info("Invoice cancelled: id={}, reason={}", invoice.getId(), reason);
    }

    @Transactional
    public void handleMarkPaid(InvoiceMarkPaidMessage message) {
        validateMarkPaidMessage(message);

        Invoice invoice = invoiceRepository.findById(message.getInvoiceId())
                .orElseThrow(() -> new InvoiceProcessingException(
                        "Invoice not found: " + message.getInvoiceId()
                ));

        validateInvoiceOwnership(invoice, message.getLocationId());

        InvoiceStatus targetStatus = resolvePaidStatus(invoice, message);
        if (invoice.getStatus() == targetStatus) {
            log.info("Invoice already in status {}, skipping mark-paid: id={}", targetStatus, message.getInvoiceId());
            return;
        }

        invoiceStateValidator.validateTransition(invoice.getStatus(), targetStatus);

        LocalDateTime eventDate = resolveDate(message.getEventDate(), null);
        invoice.setStatus(targetStatus);
        if (message.getCashReceiptId() != null) {
            invoice.setCashReceiptId(message.getCashReceiptId());
        }
        if (hasText(message.getTableId())) {
            invoice.setTableId(message.getTableId());
        }
        if (hasText(message.getTableSessionId())) {
            invoice.setTableSessionId(message.getTableSessionId());
        }
        if (targetStatus == InvoiceStatus.PAID || targetStatus == InvoiceStatus.PARTIALLY_PAID) {
            invoice.setPaidAt(eventDate);
        }
        invoiceRepository.save(invoice);

        log.info("Invoice marked as {}: id={}, cashReceiptId={}",
                invoice.getStatus(), invoice.getId(), message.getCashReceiptId());

        if (targetStatus == InvoiceStatus.PAID) {
            handleInvoicePaidSideEffects(invoice.getId(), invoice.getLocationId());
        }
    }

    @Transactional
    public void handleStatusUpdate(InvoiceStatusUpdateMessage message) {
        validateStatusUpdateMessage(message);

        Invoice invoice = invoiceRepository.findById(message.getInvoiceId())
                .orElseThrow(() -> new InvoiceProcessingException(
                        "Invoice not found: " + message.getInvoiceId()
                ));

        validateInvoiceOwnership(invoice, message.getLocationId());

        InvoiceStatus targetStatus = message.getStatus();
        InvoiceStatus currentStatus = invoice.getStatus();
        if (invoice.getStatus() == targetStatus) {
            log.info("Invoice already in status {}, skipping status update: id={}",
                    targetStatus, message.getInvoiceId());
            return;
        }

        invoiceStateValidator.validateTransition(currentStatus, targetStatus);

        LocalDateTime eventDate = resolveDate(message.getEventDate(), null);
        invoice.setStatus(targetStatus);

        if (targetStatus == InvoiceStatus.PAID || targetStatus == InvoiceStatus.PARTIALLY_PAID) {
            invoice.setPaidAt(eventDate);
        }
        if (targetStatus == InvoiceStatus.CANCELLED || targetStatus == InvoiceStatus.VOIDED) {
            invoice.setCancelledAt(eventDate);
            invoice.setCancelReason(defaultReason(message.getReason(), targetStatus.name() + " requested"));
        }

        invoiceRepository.save(invoice);
        log.info("Invoice status updated: id={}, previousStatus={}, newStatus={}",
                invoice.getId(), currentStatus, targetStatus);

        if (targetStatus == InvoiceStatus.PAID) {
            handleInvoicePaidSideEffects(invoice.getId(), invoice.getLocationId());
        }
    }

    @Transactional
    public void handleInvoicePaidSideEffects(UUID invoiceId, UUID locationId) {
        if (invoiceId == null || locationId == null) {
            log.warn("handleInvoicePaidSideEffects called with null params: invoiceId={}, locationId={}",
                    invoiceId, locationId);
            return;
        }

        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            log.warn("handleInvoicePaidSideEffects: invoice not found: {}", invoiceId);
            return;
        }

        if (invoice.getStatus() != InvoiceStatus.PAID) {
            log.warn("handleInvoicePaidSideEffects called but invoice is not PAID: id={}, status={}",
                    invoiceId, invoice.getStatus());
            return;
        }

        if (invoice.getTableId() != null) {
            log.info("[PAID_SIDE_EFFECT] Closing table: tableId={}, invoiceId={}, locationId={}",
                    invoice.getTableId(), invoiceId, locationId);
        }

        log.info("[PAID_SIDE_EFFECT] Invoice fully paid: invoiceId={}, locationId={}, totalPrice={}",
                invoiceId, locationId, invoice.getTotalPrice());
    }

    private void validateCreateMessage(InvoiceCreateMessage message) {
        if (message == null) {
            throw new InvoiceMessageValidationException("Create message is null");
        }

        requireUuid(message.getInvoiceId(), "invoiceId");
        requireNotBlank(message.getInvoiceNumber(), "invoiceNumber");
        requireUuid(message.getLocationId(), "locationId");
        requireUuid(message.getSalesId(), "salesId");
        requireNotBlank(message.getLocationName(), "locationName");
        requirePositive(message.getSubtotal(), "subtotal");
        requirePositive(message.getTotalPrice(), "totalPrice");
        validateDiscountPercentage(message.getDiscountPercentage());
        validateDetails(message.getDetails());
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

    private void validateStatusUpdateMessage(InvoiceStatusUpdateMessage message) {
        if (message == null) {
            throw new InvoiceMessageValidationException("StatusUpdate message is null");
        }
        requireUuid(message.getInvoiceId(), "invoiceId");
        requireUuid(message.getLocationId(), "locationId");
        if (message.getStatus() == null) {
            throw new InvoiceMessageValidationException("Required field is missing: status");
        }
    }

    private void validateDiscountPercentage(BigDecimal discountPercentage) {
        if (discountPercentage == null) {
            return;
        }
        if (discountPercentage.compareTo(BigDecimal.ZERO) < 0
                || discountPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new InvoiceMessageValidationException("discountPercentage must be between 0 and 100");
        }
    }

    private void validateDetails(Collection<InvoiceItemMessage> details) {
        if (details == null || details.isEmpty()) {
            throw new InvoiceMessageValidationException("Required field is missing or empty: details");
        }

        int index = 0;
        for (InvoiceItemMessage item : details) {
            if (item == null) {
                throw new InvoiceMessageValidationException("Detail item is null at index " + index);
            }
            requirePositive(item.getQuantity(), "details[" + index + "].quantity");
            requirePositive(item.getUnitPrice(), "details[" + index + "].unitPrice");
            requirePositive(item.getSubtotal(), "details[" + index + "].subtotal");
            index++;
        }
    }

    private void validateInvoiceOwnership(Invoice invoice, UUID locationId) {
        if (!invoice.getLocationId().equals(locationId)) {
            throw new InvoiceProcessingException("Invoice does not belong to location: " + locationId);
        }
    }

    private void requireUuid(UUID value, String fieldName) {
        if (Objects.isNull(value)) {
            throw new InvoiceMessageValidationException("Required field is missing: " + fieldName);
        }
    }

    private void requireNotBlank(String value, String fieldName) {
        if (!hasText(value)) {
            throw new InvoiceMessageValidationException("Required field is missing or blank: " + fieldName);
        }
    }

    private void requirePositive(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new InvoiceMessageValidationException("Required field is missing: " + fieldName);
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceMessageValidationException(fieldName + " must be > 0");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultReason(String reason, String fallback) {
        return hasText(reason) ? reason : fallback;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private LocalDateTime resolveDate(LocalDateTime primary, LocalDateTime fallback) {
        if (primary != null) {
            return primary;
        }
        if (fallback != null) {
            return fallback;
        }
        return LocalDateTime.now();
    }

    private InvoiceStatus resolvePaidStatus(Invoice invoice, InvoiceMarkPaidMessage message) {
        if (message.getPaidAmount() != null
                && invoice.getTotalPrice() != null
                && message.getPaidAmount().compareTo(invoice.getTotalPrice()) < 0) {
            return InvoiceStatus.PARTIALLY_PAID;
        }
        return InvoiceStatus.PAID;
    }

    private boolean matchesPrimaryData(Invoice existing, InvoiceCreateMessage message) {
        return Objects.equals(existing.getInvoiceNumber(), message.getInvoiceNumber())
                && Objects.equals(existing.getLocationId(), message.getLocationId())
                && Objects.equals(existing.getSalesId(), message.getSalesId())
                && Objects.equals(existing.getLocationName(), message.getLocationName())
                && Objects.equals(existing.getTableId(), message.getTableId())
                && Objects.equals(existing.getTableSessionId(), message.getTableSessionId())
                && compareBigDecimal(existing.getSubtotal(), message.getSubtotal())
                && compareBigDecimal(existing.getTotalPrice(), message.getTotalPrice());
    }

    private boolean compareBigDecimal(BigDecimal first, BigDecimal second) {
        if (first == null && second == null) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.compareTo(second) == 0;
    }
}
