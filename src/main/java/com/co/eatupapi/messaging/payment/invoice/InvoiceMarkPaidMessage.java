package com.co.eatupapi.messaging.payment.invoice;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class InvoiceMarkPaidMessage {
    private UUID locationId;
    private UUID invoiceId;
    private UUID cashReceiptId;
    private BigDecimal paidAmount;
    private UUID paymentMethodId;
    private LocalDateTime eventDate;
}
