package com.co.eatupapi.repositories.payment.invoice;

import com.co.eatupapi.domain.payment.invoice.Invoice;
import com.co.eatupapi.domain.payment.invoice.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByLocationId(UUID locationId, Pageable pageable);

    Optional<Invoice> findByIdAndLocationId(UUID id, UUID locationId);

    boolean existsByInvoiceNumberAndLocationId(String invoiceNumber, UUID locationId);

    boolean existsBySalesIdAndLocationIdAndStatusNotIn(
            UUID salesId,
            UUID locationId,
            Collection<InvoiceStatus> statuses
    );
}
