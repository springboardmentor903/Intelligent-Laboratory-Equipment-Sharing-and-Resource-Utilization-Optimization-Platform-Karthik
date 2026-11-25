package com.labresource.platform.billing.repository;

import com.labresource.platform.billing.entity.UsageCharge;
import com.labresource.platform.billing.model.ChargeStatus;
import com.labresource.platform.billing.model.ChargeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageChargeRepository extends JpaRepository<UsageCharge, Long>, JpaSpecificationExecutor<UsageCharge> {

    boolean existsByBookingId(Long bookingId);

    Optional<UsageCharge> findByBookingId(Long bookingId);

    List<UsageCharge> findByInvoiceId(Long invoiceId);

    // Pending charges for inter-institution invoicing
    @Query("SELECT c FROM UsageCharge c WHERE c.institutionId = :instId AND c.chargeType = :chargeType " +
           "AND c.status = 'PENDING' AND c.invoiceId IS NULL AND c.userInstitutionId IS NOT NULL " +
           "AND c.createdAt >= :start AND c.createdAt <= :end")
    List<UsageCharge> findPendingExternalChargesForInvoicing(
            @Param("instId") Long institutionId,
            @Param("chargeType") ChargeType chargeType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Pending charges for department chargeback invoicing
    @Query("SELECT c FROM UsageCharge c WHERE c.institutionId = :instId AND c.chargeType = :chargeType " +
           "AND c.status = 'PENDING' AND c.invoiceId IS NULL AND c.departmentId IS NOT NULL " +
           "AND c.createdAt >= :start AND c.createdAt <= :end")
    List<UsageCharge> findPendingInternalChargesForInvoicing(
            @Param("instId") Long institutionId,
            @Param("chargeType") ChargeType chargeType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Total spend of a department in a date range (excluding CANCELLED charges)
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM UsageCharge c " +
           "WHERE c.departmentId = :deptId AND c.status != 'CANCELLED' " +
           "AND c.createdAt >= :start AND c.createdAt <= :end")
    BigDecimal sumDepartmentSpend(
            @Param("deptId") Long departmentId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Total amount of pending un-invoiced charges for an institution
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM UsageCharge c " +
           "WHERE c.institutionId = :instId AND c.status = 'PENDING' AND c.invoiceId IS NULL")
    BigDecimal sumPendingChargesAmount(@Param("instId") Long institutionId);

    @Query("SELECT COUNT(c) FROM UsageCharge c " +
           "WHERE c.institutionId = :instId AND c.status = 'PENDING' AND c.invoiceId IS NULL")
    long countPendingCharges(@Param("instId") Long institutionId);

    @Query("SELECT c.equipmentId, COALESCE(SUM(c.amount), 0) FROM UsageCharge c " +
           "WHERE c.equipmentId IN :equipmentIds AND c.status != 'CANCELLED' GROUP BY c.equipmentId")
    List<Object[]> sumChargesByEquipmentIds(@Param("equipmentIds") List<Long> equipmentIds);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM UsageCharge c " +
           "WHERE c.userId = :userId AND c.status != 'CANCELLED' AND c.createdAt >= :since")
    BigDecimal sumUserChargesSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM UsageCharge c " +
           "WHERE c.institutionId = :instId AND c.status != 'CANCELLED'")
    BigDecimal sumTotalChargesByInstitution(@Param("instId") Long institutionId);
}
