package com.co.eatupapi.repositories.payment.cashreceipt;

import com.co.eatupapi.domain.payment.cashreceipt.CashReceipt;
import com.co.eatupapi.domain.payment.cashreceipt.CashReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CashReceiptRepository extends JpaRepository<CashReceipt, UUID> {
    @Query("""
            SELECT COALESCE(SUM(cr.amount), 0)
            FROM CashReceipt cr
            WHERE cr.invoiceId = :invoiceId
              AND cr.status = :status
            """)
    BigDecimal sumByInvoiceAndStatus(@Param("invoiceId") UUID invoiceId,
                                     @Param("status") CashReceiptStatus status);

    List<CashReceipt> findByInvoiceIdAndStatus(UUID invoiceId, CashReceiptStatus status);
}
