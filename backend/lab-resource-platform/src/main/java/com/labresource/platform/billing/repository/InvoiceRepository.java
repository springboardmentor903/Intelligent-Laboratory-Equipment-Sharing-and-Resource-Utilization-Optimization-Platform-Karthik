package com.labresource.platform.billing.repository;

import com.labresource.platform.billing.entity.Invoice;
import com.labresource.platform.billing.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("SELECT i FROM Invoice i WHERE i.institutionId = :instId ORDER BY i.createdAt DESC")
    List<Invoice> findAllIssuedByInstitution(@Param("instId") Long institutionId);

    @Query("SELECT i FROM Invoice i WHERE i.recipientInstitutionId = :instId ORDER BY i.createdAt DESC")
    List<Invoice> findAllReceivedByInstitution(@Param("instId") Long recipientInstitutionId);

    @Query("SELECT i FROM Invoice i WHERE i.institutionId = :instId " +
           "AND (:start IS NULL OR i.periodEnd >= :start) " +
           "AND (:end IS NULL OR i.periodStart <= :end)")
    List<Invoice> findIssuedForSummary(
            @Param("instId") Long institutionId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT i FROM Invoice i WHERE i.recipientInstitutionId = :instId " +
           "AND (:start IS NULL OR i.periodEnd >= :start) " +
           "AND (:end IS NULL OR i.periodStart <= :end)")
    List<Invoice> findReceivedForSummary(
            @Param("instId") Long recipientInstitutionId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
