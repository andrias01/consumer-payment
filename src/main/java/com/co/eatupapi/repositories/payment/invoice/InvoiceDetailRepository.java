package com.co.eatupapi.repositories.payment.invoice;

import com.co.eatupapi.domain.payment.invoice.InvoiceDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InvoiceDetailRepository extends JpaRepository<InvoiceDetail, UUID> {
}
