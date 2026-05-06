package com.co.eatupapi.messaging.payment.invoice;

import com.co.eatupapi.domain.payment.invoice.InvoiceStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class InvoiceCreateMessage {
    private UUID invoiceId;
    private String invoiceNumber;
    private UUID locationId;
    private UUID salesId;
    private UUID customerDiscountId;
    private UUID customerId;
    private UUID discountId;
    private BigDecimal discountPercentage;
    private String discountDescription;
    private String tableId;
    private String locationName;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalPrice;
    private InvoiceStatus status;
    private LocalDateTime invoiceDate;
    private List<InvoiceItemMessage> details;
    private LocalDateTime eventDate;
}
