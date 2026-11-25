package com.labresource.platform.billing.repository;

import com.labresource.platform.billing.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoiceIdOrderByCreatedAtAsc(Long invoiceId);

    void deleteByInvoiceId(Long invoiceId);
}
