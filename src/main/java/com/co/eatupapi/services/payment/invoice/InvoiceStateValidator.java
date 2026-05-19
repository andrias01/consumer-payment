package com.co.eatupapi.services.payment.invoice;

import com.co.eatupapi.domain.payment.invoice.InvoiceStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class InvoiceStateValidator {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED_TRANSITIONS = Map.of(
            InvoiceStatus.OPEN, EnumSet.of(
                    InvoiceStatus.PENDING,
                    InvoiceStatus.PAID,
                    InvoiceStatus.CLOSED,
                    InvoiceStatus.CANCELLED,
                    InvoiceStatus.VOIDED
            ),
            InvoiceStatus.PENDING, EnumSet.of(
                    InvoiceStatus.PARTIALLY_PAID,
                    InvoiceStatus.PAID,
                    InvoiceStatus.VOIDED,
                    InvoiceStatus.CANCELLED
            ),
            InvoiceStatus.PARTIALLY_PAID, EnumSet.of(
                    InvoiceStatus.PAID
            ),
            InvoiceStatus.PAID, EnumSet.noneOf(InvoiceStatus.class),
            InvoiceStatus.VOIDED, EnumSet.noneOf(InvoiceStatus.class),
            InvoiceStatus.CLOSED, EnumSet.noneOf(InvoiceStatus.class),
            InvoiceStatus.CANCELLED, EnumSet.noneOf(InvoiceStatus.class)
    );

    public boolean canTransition(InvoiceStatus currentStatus, InvoiceStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return true;
        }
        Set<InvoiceStatus> allowedTargets = ALLOWED_TRANSITIONS.get(currentStatus);
        return allowedTargets != null && allowedTargets.contains(targetStatus);
    }

    public void validateTransition(InvoiceStatus currentStatus, InvoiceStatus targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            throw new InvoiceProcessingException(
                    "Invalid invoice status transition: " + currentStatus + " -> " + targetStatus
            );
        }
    }
}
