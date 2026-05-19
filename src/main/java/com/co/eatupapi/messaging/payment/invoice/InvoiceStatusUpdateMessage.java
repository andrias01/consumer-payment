package com.co.eatupapi.messaging.payment.invoice;

import com.co.eatupapi.domain.payment.invoice.InvoiceStatus;
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
public class InvoiceStatusUpdateMessage {
    private UUID locationId;
    private UUID invoiceId;
    private InvoiceStatus status;
    private String reason;
    private LocalDateTime eventDate;
}
