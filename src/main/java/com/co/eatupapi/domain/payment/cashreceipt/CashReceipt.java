package com.co.eatupapi.domain.payment.cashreceipt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cash_receipts")
public class CashReceipt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private UUID paymentMethodId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashReceiptStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime cancelledAt;
}
