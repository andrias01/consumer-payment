package com.co.eatupapi.messaging.payment.invoice;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class InvoiceCancelMessage {
    private UUID locationId;
    private UUID invoiceId;
    private String reason;
    private LocalDateTime eventDate;
}
