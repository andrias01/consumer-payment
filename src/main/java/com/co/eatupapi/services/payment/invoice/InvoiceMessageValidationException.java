package com.co.eatupapi.services.payment.invoice;

public class InvoiceMessageValidationException extends RuntimeException {

    public InvoiceMessageValidationException(String message) {
        super(message);
    }

    public InvoiceMessageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
