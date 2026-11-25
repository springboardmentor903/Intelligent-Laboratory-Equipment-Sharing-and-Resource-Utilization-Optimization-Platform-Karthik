package com.labresource.platform.billing.service;

import com.labresource.platform.billing.entity.Invoice;
import com.labresource.platform.billing.entity.UsageCharge;
import com.labresource.platform.billing.model.*;
import com.labresource.platform.billing.repository.InvoiceRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BillingAnalyticsService {

    private final UsageChargeRepository usageChargeRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final InvoiceRepository invoiceRepository;

    @Autowired
    public BillingAnalyticsService(
            UsageChargeRepository usageChargeRepository,
            EquipmentRepository equipmentRepository,
            DepartmentRepository departmentRepository,
            WorkOrderRepository workOrderRepository,
            InvoiceRepository invoiceRepository) {
        this.usageChargeRepository = usageChargeRepository;
        this.equipmentRepository = equipmentRepository;
        this.departmentRepository = departmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Equipment Cost API:
     * Return usage hours, internal cost, external cost, unassigned cost, total usage cost, and maintenance cost.
     * Keep maintenance costs strictly separate from usage costs.
     */
    @Transactional(readOnly = true)
    public List<EquipmentCostDTO> getEquipmentCosts(Long institutionId, Long departmentId, LocalDate startDate, LocalDate endDate) {
        List<Equipment> equipmentList;
        if (departmentId != null) {
            equipmentList = equipmentRepository.findByInstitutionIdAndDepartmentId(institutionId, departmentId);
        } else {
            equipmentList = equipmentRepository.findByInstitutionId(institutionId);
        }

        if (equipmentList.isEmpty()) {
            return List.of();
        }

        List<Long> equipIds = equipmentList.stream().map(Equipment::getId).toList();
        LocalDateTime startDt = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDt = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        // Fetch usage charges in scope (excluding CANCELLED)
        Specification<UsageCharge> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("institutionId"), institutionId));
            predicates.add(root.get("equipmentId").in(equipIds));
            predicates.add(cb.notEqual(root.get("status"), ChargeStatus.CANCELLED));
            if (startDt != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDt));
            }
            if (endDt != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDt));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<UsageCharge> charges = usageChargeRepository.findAll(spec);
        Map<Long, List<UsageCharge>> chargesByEquip = charges.stream()
                .collect(Collectors.groupingBy(UsageCharge::getEquipmentId));

        // Fetch maintenance costs for equipment
        Map<Long, BigDecimal> maintenanceCostMap = new HashMap<>();
        try {
            List<Object[]> costRows = workOrderRepository.sumCompletedCostByEquipmentIdsAndPeriod(equipIds, startDt, endDt);
            for (Object[] row : costRows) {
                if (row[0] != null && row[1] != null) {
                    maintenanceCostMap.put((Long) row[0], ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP));
                }
            }
        } catch (Exception ignored) {
        }

        Map<Long, Department> deptMap = departmentRepository.findByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Department::getId, d -> d));

        return equipmentList.stream().map(eq -> {
            List<UsageCharge> eqCharges = chargesByEquip.getOrDefault(eq.getId(), List.of());
            long totalMinutes = eqCharges.stream().mapToLong(UsageCharge::getUsageMinutes).sum();
            BigDecimal usageHours = BigDecimal.valueOf(totalMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            BigDecimal internalCost = eqCharges.stream()
                    .filter(c -> c.getChargeType() == ChargeType.INTERNAL)
                    .map(UsageCharge::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal externalCost = eqCharges.stream()
                    .filter(c -> c.getChargeType() == ChargeType.EXTERNAL)
                    .map(UsageCharge::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal unassignedCost = eqCharges.stream()
                    .filter(c -> c.getChargeType() == ChargeType.UNASSIGNED)
                    .map(UsageCharge::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal totalUsageCost = internalCost.add(externalCost).add(unassignedCost)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal maintenanceCost = maintenanceCostMap.getOrDefault(eq.getId(), BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            Department dept = eq.getDepartmentId() != null ? deptMap.get(eq.getDepartmentId()) : null;

            return EquipmentCostDTO.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .assetTag(eq.getAssetTag())
                    .departmentId(eq.getDepartmentId())
                    .departmentName(dept != null ? dept.getName() : "Unassigned")
                    .usageMinutes(totalMinutes)
                    .usageHours(usageHours)
                    .internalCost(internalCost)
                    .externalCost(externalCost)
                    .unassignedCost(unassignedCost)
                    .totalUsageCost(totalUsageCost)
                    .maintenanceCost(maintenanceCost)
                    .build();
        }).sorted(Comparator.comparing(EquipmentCostDTO::getTotalUsageCost, Comparator.reverseOrder()))
          .toList();
    }

    /**
     * Department Allocation API:
     * Return internal cost, external cost, total cost, and percentage share,
     * plus unallocated charges for users without a department.
     */
    @Transactional(readOnly = true)
    public DepartmentAllocationResponse getDepartmentAllocations(Long institutionId, LocalDate startDate, LocalDate endDate) {
        List<Department> departments = departmentRepository.findByInstitutionId(institutionId);

        LocalDateTime startDt = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDt = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        Specification<UsageCharge> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("institutionId"), institutionId));
            predicates.add(cb.notEqual(root.get("status"), ChargeStatus.CANCELLED));
            if (startDt != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDt));
            }
            if (endDt != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDt));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<UsageCharge> charges = usageChargeRepository.findAll(spec);
        BigDecimal grandTotal = charges.stream()
                .map(UsageCharge::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<Long, List<UsageCharge>> byDept = charges.stream()
                .filter(c -> c.getDepartmentId() != null)
                .collect(Collectors.groupingBy(UsageCharge::getDepartmentId));

        List<DepartmentAllocationDTO> allocations = departments.stream().map(dept -> {
            List<UsageCharge> deptCharges = byDept.getOrDefault(dept.getId(), List.of());
            BigDecimal internal = deptCharges.stream()
                    .filter(c -> c.getChargeType() == ChargeType.INTERNAL)
                    .map(UsageCharge::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal external = deptCharges.stream()
                    .filter(c -> c.getChargeType() == ChargeType.EXTERNAL)
                    .map(UsageCharge::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal total = internal.add(external).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = grandTotal.compareTo(BigDecimal.ZERO) > 0
                    ? total.multiply(BigDecimal.valueOf(100)).divide(grandTotal, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            return DepartmentAllocationDTO.builder()
                    .departmentId(dept.getId())
                    .departmentName(dept.getName())
                    .internalCost(internal)
                    .externalCost(external)
                    .totalCost(total)
                    .percentageShare(share)
                    .chargeCount(deptCharges.size())
                    .build();
        }).sorted(Comparator.comparing(DepartmentAllocationDTO::getTotalCost, Comparator.reverseOrder()))
          .toList();

        // Unallocated charges (departmentId == null)
        List<UsageCharge> unallocatedList = charges.stream()
                .filter(c -> c.getDepartmentId() == null)
                .toList();

        BigDecimal unallocatedInternal = unallocatedList.stream()
                .filter(c -> c.getChargeType() == ChargeType.INTERNAL)
                .map(UsageCharge::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal unallocatedExternal = unallocatedList.stream()
                .filter(c -> c.getChargeType() == ChargeType.EXTERNAL)
                .map(UsageCharge::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal unallocatedTotal = unallocatedList.stream()
                .map(UsageCharge::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal unallocatedShare = grandTotal.compareTo(BigDecimal.ZERO) > 0
                ? unallocatedTotal.multiply(BigDecimal.valueOf(100)).divide(grandTotal, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        DepartmentAllocationDTO unallocatedDto = DepartmentAllocationDTO.builder()
                .departmentId(null)
                .departmentName("Unallocated / No Department")
                .internalCost(unallocatedInternal)
                .externalCost(unallocatedExternal)
                .totalCost(unallocatedTotal)
                .percentageShare(unallocatedShare)
                .chargeCount(unallocatedList.size())
                .build();

        return DepartmentAllocationResponse.builder()
                .allocations(allocations)
                .unallocated(unallocatedDto)
                .grandTotalCost(grandTotal)
                .build();
    }

    /**
     * Financial Summary API:
     * Return invoiced, paid, outstanding, and overdue amounts for both issued and received invoices.
     * Include pending (un-invoiced) charges in the summary.
     */
    @Transactional(readOnly = true)
    public FinancialSummaryDTO getFinancialSummary(Long institutionId, LocalDate startDate, LocalDate endDate) {
        List<Invoice> issued = invoiceRepository.findIssuedForSummary(institutionId, startDate, endDate);
        List<Invoice> received = invoiceRepository.findReceivedForSummary(institutionId, startDate, endDate);

        // Issued amounts (excluding CANCELLED)
        BigDecimal issuedInvoiced = issued.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal issuedPaid = issued.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal issuedOutstanding = issued.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.ISSUED || i.getStatus() == InvoiceStatus.DRAFT)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal issuedOverdue = issued.stream()
                .filter(Invoice::isOverdue)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Received amounts (excluding CANCELLED)
        BigDecimal receivedInvoiced = received.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal receivedPaid = received.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal receivedOutstanding = received.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.ISSUED || i.getStatus() == InvoiceStatus.DRAFT)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal receivedOverdue = received.stream()
                .filter(Invoice::isOverdue)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Pending un-invoiced charges
        BigDecimal pendingChargesAmount = usageChargeRepository.sumPendingChargesAmount(institutionId);
        if (pendingChargesAmount == null) pendingChargesAmount = BigDecimal.ZERO;
        pendingChargesAmount = pendingChargesAmount.setScale(2, RoundingMode.HALF_UP);
        long pendingChargesCount = usageChargeRepository.countPendingCharges(institutionId);

        return FinancialSummaryDTO.builder()
                .issuedInvoiced(issuedInvoiced)
                .issuedPaid(issuedPaid)
                .issuedOutstanding(issuedOutstanding)
                .issuedOverdue(issuedOverdue)
                .receivedInvoiced(receivedInvoiced)
                .receivedPaid(receivedPaid)
                .receivedOutstanding(receivedOutstanding)
                .receivedOverdue(receivedOverdue)
                .pendingChargesAmount(pendingChargesAmount)
                .pendingChargesCount(pendingChargesCount)
                .build();
    }
}
