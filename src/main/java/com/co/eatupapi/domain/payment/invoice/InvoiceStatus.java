package com.co.eatupapi.domain.payment.invoice;

public enum InvoiceStatus {
    OPEN,
    PENDING,
    PARTIALLY_PAID,
    PAID,
    VOIDED,
    CLOSED,
    CANCELLED
}
