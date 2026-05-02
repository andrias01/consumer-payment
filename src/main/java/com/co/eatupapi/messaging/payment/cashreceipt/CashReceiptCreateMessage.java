package com.co.eatupapi.messaging.payment.cashreceipt;

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
public class CashReceiptCreateMessage {
    private UUID locationId;
    private UUID invoiceId;
    private BigDecimal amount;
    private UUID paymentMethodId;
    private LocalDateTime eventDate;
}
