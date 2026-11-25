package com.labresource.platform.billing.service;

import com.labresource.platform.billing.entity.Invoice;
import com.labresource.platform.billing.entity.InvoiceItem;
import com.labresource.platform.billing.entity.UsageCharge;
import com.labresource.platform.billing.model.*;
import com.labresource.platform.billing.repository.InvoiceItemRepository;
import com.labresource.platform.billing.repository.InvoiceRepository;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final UsageChargeRepository usageChargeRepository;
    private final InstitutionRepository institutionRepository;
    private final DepartmentRepository departmentRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final UsageChargeService usageChargeService;

    private final BigDecimal defaultTaxRate;
    private final int paymentTermsDays;

    @Autowired
    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            UsageChargeRepository usageChargeRepository,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            UsageChargeService usageChargeService,
            @Value("${app.billing.tax-rate:18.00}") BigDecimal defaultTaxRate,
            @Value("${app.billing.payment-terms-days:30}") int paymentTermsDays) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.usageChargeRepository = usageChargeRepository;
        this.institutionRepository = institutionRepository;
        this.departmentRepository = departmentRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.usageChargeService = usageChargeService;
        this.defaultTaxRate = defaultTaxRate != null ? defaultTaxRate : new BigDecimal("18.00");
        this.paymentTermsDays = paymentTermsDays > 0 ? paymentTermsDays : 30;
    }

    /**
     * Generates unique invoice numbers in format INV-YYYY-XXXXXX (e.g. INV-2026-000001).
     */
    public synchronized String generateNextInvoiceNumber(int year) {
        List<Invoice> existing = invoiceRepository.findAll();
        long maxSeq = 0;
        String prefix = "INV-" + year + "-";
        for (Invoice inv : existing) {
            if (inv.getInvoiceNumber() != null && inv.getInvoiceNumber().startsWith(prefix)) {
                try {
                    String seqStr = inv.getInvoiceNumber().substring(prefix.length());
                    long seq = Long.parseLong(seqStr);
                    if (seq > maxSeq) {
                        maxSeq = seq;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return String.format("INV-%d-%06d", year, maxSeq + 1);
    }

    /**
     * Generates draft invoices from pending charges for a selected period.
     * - Inter-institution invoices (EXTERNAL charges): grouped by user_institution_id.
     * - Department chargeback invoices (INTERNAL charges): grouped by department_id.
     * - Excludes UNASSIGNED charges and charges without a department.
     */
    @Transactional
    public List<InvoiceDTO> generateInvoices(Long institutionId, GenerateInvoiceRequest request) {
        if (request.getPeriodStart() == null || request.getPeriodEnd() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Period start and end dates are required.");
        }
        if (request.getPeriodEnd().isBefore(request.getPeriodStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Period end date cannot be before start date.");
        }
        if (request.getInvoiceType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice type (INTER_INSTITUTION or DEPARTMENT_CHARGEBACK) is required.");
        }

        LocalDateTime start = request.getPeriodStart().atStartOfDay();
        LocalDateTime end = request.getPeriodEnd().atTime(LocalTime.MAX);
        int currentYear = LocalDate.now().getYear();

        List<Invoice> createdInvoices = new ArrayList<>();

        if (request.getInvoiceType() == InvoiceType.INTER_INSTITUTION) {
            List<UsageCharge> pendingCharges = usageChargeRepository.findPendingExternalChargesForInvoicing(
                    institutionId, ChargeType.EXTERNAL, start, end);

            // Group charges by userInstitutionId
            Map<Long, List<UsageCharge>> byInstitution = pendingCharges.stream()
                    .filter(c -> c.getUserInstitutionId() != null)
                    .filter(c -> request.getRecipientInstitutionId() == null || c.getUserInstitutionId().equals(request.getRecipientInstitutionId()))
                    .collect(Collectors.groupingBy(UsageCharge::getUserInstitutionId));

            for (Map.Entry<Long, List<UsageCharge>> entry : byInstitution.entrySet()) {
                Long recipientInstId = entry.getKey();
                List<UsageCharge> charges = entry.getValue();
                if (charges.isEmpty()) continue;

                BigDecimal subtotal = charges.stream()
                        .map(UsageCharge::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);

                BigDecimal taxRate = defaultTaxRate.setScale(2, RoundingMode.HALF_UP);
                BigDecimal taxAmount = subtotal.multiply(taxRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal totalAmount = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

                String invoiceNumber = generateNextInvoiceNumber(currentYear);
                LocalDate dueDate = LocalDate.now().plusDays(paymentTermsDays);

                Invoice invoice = Invoice.builder()
                        .invoiceNumber(invoiceNumber)
                        .institutionId(institutionId)
                        .recipientInstitutionId(recipientInstId)
                        .recipientDepartmentId(null)
                        .invoiceType(InvoiceType.INTER_INSTITUTION)
                        .periodStart(request.getPeriodStart())
                        .periodEnd(request.getPeriodEnd())
                        .subtotal(subtotal)
                        .taxRate(taxRate)
                        .taxAmount(taxAmount)
                        .totalAmount(totalAmount)
                        .dueDate(dueDate)
                        .status(InvoiceStatus.DRAFT)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                Invoice savedInvoice = invoiceRepository.save(invoice);
                linkChargesAndCreateItems(savedInvoice, charges);
                createdInvoices.add(savedInvoice);
            }

        } else if (request.getInvoiceType() == InvoiceType.DEPARTMENT_CHARGEBACK) {
            List<UsageCharge> pendingCharges = usageChargeRepository.findPendingInternalChargesForInvoicing(
                    institutionId, ChargeType.INTERNAL, start, end);

            // Group charges by departmentId
            Map<Long, List<UsageCharge>> byDepartment = pendingCharges.stream()
                    .filter(c -> c.getDepartmentId() != null)
                    .filter(c -> request.getRecipientDepartmentId() == null || c.getDepartmentId().equals(request.getRecipientDepartmentId()))
                    .collect(Collectors.groupingBy(UsageCharge::getDepartmentId));

            for (Map.Entry<Long, List<UsageCharge>> entry : byDepartment.entrySet()) {
                Long deptId = entry.getKey();
                List<UsageCharge> charges = entry.getValue();
                if (charges.isEmpty()) continue;

                BigDecimal subtotal = charges.stream()
                        .map(UsageCharge::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);

                // Department chargebacks have 0% tax
                BigDecimal taxRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal taxAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal totalAmount = subtotal;

                String invoiceNumber = generateNextInvoiceNumber(currentYear);
                LocalDate dueDate = LocalDate.now().plusDays(paymentTermsDays);

                Invoice invoice = Invoice.builder()
                        .invoiceNumber(invoiceNumber)
                        .institutionId(institutionId)
                        .recipientInstitutionId(institutionId)
                        .recipientDepartmentId(deptId)
                        .invoiceType(InvoiceType.DEPARTMENT_CHARGEBACK)
                        .periodStart(request.getPeriodStart())
                        .periodEnd(request.getPeriodEnd())
                        .subtotal(subtotal)
                        .taxRate(taxRate)
                        .taxAmount(taxAmount)
                        .totalAmount(totalAmount)
                        .dueDate(dueDate)
                        .status(InvoiceStatus.DRAFT)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                Invoice savedInvoice = invoiceRepository.save(invoice);
                linkChargesAndCreateItems(savedInvoice, charges);
                createdInvoices.add(savedInvoice);
            }
        }

        return createdInvoices.stream().map(this::mapToDTO).toList();
    }

    private void linkChargesAndCreateItems(Invoice invoice, List<UsageCharge> charges) {
        Set<Long> equipIds = charges.stream().map(UsageCharge::getEquipmentId).collect(Collectors.toSet());
        Set<Long> userIds = charges.stream().map(UsageCharge::getUserId).collect(Collectors.toSet());

        Map<Long, String> equipNameMap = equipmentRepository.findAllById(equipIds).stream()
                .collect(Collectors.toMap(Equipment::getId, Equipment::getName));
        Map<Long, String> userNameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getFullName() != null ? u.getFullName() : u.getEmail()));

        for (UsageCharge charge : charges) {
            charge.setInvoiceId(invoice.getId());
            charge.setStatus(ChargeStatus.INVOICED);
            usageChargeRepository.save(charge);

            InvoiceItem item = InvoiceItem.builder()
                    .invoiceId(invoice.getId())
                    .chargeId(charge.getId())
                    .equipmentId(charge.getEquipmentId())
                    .equipmentName(equipNameMap.getOrDefault(charge.getEquipmentId(), "Equipment #" + charge.getEquipmentId()))
                    .userId(charge.getUserId())
                    .userName(userNameMap.getOrDefault(charge.getUserId(), "User #" + charge.getUserId()))
                    .usageMinutes(charge.getUsageMinutes())
                    .hourlyRate(charge.getHourlyRate())
                    .amount(charge.getAmount())
                    .createdAt(LocalDateTime.now())
                    .build();
            invoiceItemRepository.save(item);
        }
    }

    @Transactional
    public InvoiceDTO issueInvoice(Long invoiceId, Long actorInstitutionId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        if (!invoice.getInstitutionId().equals(actorInstitutionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only issue invoices belonging to your institution.");
        }
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT invoices can be issued. Current status: " + invoice.getStatus());
        }

        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);

        // Ensure all linked charges have status INVOICED
        List<UsageCharge> charges = usageChargeRepository.findByInvoiceId(invoiceId);
        for (UsageCharge c : charges) {
            c.setStatus(ChargeStatus.INVOICED);
            usageChargeRepository.save(c);
        }

        return mapToDTO(saved);
    }

    @Transactional
    public InvoiceDTO markInvoiceAsPaid(Long invoiceId, Long actorInstitutionId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        if (!invoice.getInstitutionId().equals(actorInstitutionId)
                && (invoice.getRecipientInstitutionId() == null || !invoice.getRecipientInstitutionId().equals(actorInstitutionId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to invoice.");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return mapToDTO(invoice);
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot mark a CANCELLED invoice as paid.");
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);

        // Update all linked charges to PAID
        List<UsageCharge> charges = usageChargeRepository.findByInvoiceId(invoiceId);
        for (UsageCharge c : charges) {
            c.setStatus(ChargeStatus.PAID);
            usageChargeRepository.save(c);
        }

        return mapToDTO(saved);
    }

    @Transactional
    public InvoiceDTO cancelInvoice(Long invoiceId, Long actorInstitutionId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        if (!invoice.getInstitutionId().equals(actorInstitutionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the issuing institution can cancel an invoice.");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot cancel a PAID invoice.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            return mapToDTO(invoice);
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);

        // Revert all linked charges to PENDING and release invoice link
        List<UsageCharge> charges = usageChargeRepository.findByInvoiceId(invoiceId);
        for (UsageCharge c : charges) {
            c.setInvoiceId(null);
            c.setStatus(ChargeStatus.PENDING);
            usageChargeRepository.save(c);
        }

        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceDTO> getIssuedInvoices(Long institutionId, InvoiceStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("institutionId"), institutionId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("periodEnd"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("periodStart"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Invoice> page = invoiceRepository.findAll(spec, pageable);
        return page.map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceDTO> getReceivedInvoices(Long recipientInstitutionId, Long recipientDepartmentId, InvoiceStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (recipientDepartmentId != null) {
                predicates.add(cb.equal(root.get("recipientDepartmentId"), recipientDepartmentId));
            } else if (recipientInstitutionId != null) {
                predicates.add(cb.equal(root.get("recipientInstitutionId"), recipientInstitutionId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("periodEnd"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("periodStart"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Invoice> page = invoiceRepository.findAll(spec, pageable);
        return page.map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public InvoiceDetailDTO getInvoiceDetail(Long invoiceId, Long actorInstitutionId, Long actorDepartmentId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

        boolean canAccess = invoice.getInstitutionId().equals(actorInstitutionId)
                || (invoice.getRecipientInstitutionId() != null && invoice.getRecipientInstitutionId().equals(actorInstitutionId))
                || (actorDepartmentId != null && invoice.getRecipientDepartmentId() != null && invoice.getRecipientDepartmentId().equals(actorDepartmentId));

        if (!canAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to invoice.");
        }

        InvoiceDTO invoiceDTO = mapToDTO(invoice);
        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
        List<InvoiceItemDTO> itemDTOs = items.stream().map(it -> {
            BigDecimal usageHours = BigDecimal.valueOf(it.getUsageMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            return InvoiceItemDTO.builder()
                    .id(it.getId())
                    .invoiceId(it.getInvoiceId())
                    .chargeId(it.getChargeId())
                    .equipmentId(it.getEquipmentId())
                    .equipmentName(it.getEquipmentName())
                    .userId(it.getUserId())
                    .userName(it.getUserName())
                    .usageMinutes(it.getUsageMinutes())
                    .usageHours(usageHours)
                    .hourlyRate(it.getHourlyRate())
                    .amount(it.getAmount())
                    .createdAt(it.getCreatedAt())
                    .build();
        }).toList();

        List<UsageCharge> charges = usageChargeRepository.findByInvoiceId(invoiceId);
        List<UsageChargeDTO> chargeDTOs = usageChargeService.mapToDTOList(charges);

        return InvoiceDetailDTO.builder()
                .invoice(invoiceDTO)
                .items(itemDTOs)
                .charges(chargeDTOs)
                .build();
    }

    public InvoiceDTO mapToDTO(Invoice invoice) {
        if (invoice == null) return null;

        Institution issuer = institutionRepository.findById(invoice.getInstitutionId()).orElse(null);
        Institution recipientInst = invoice.getRecipientInstitutionId() != null
                ? institutionRepository.findById(invoice.getRecipientInstitutionId()).orElse(null) : null;
        Department recipientDept = invoice.getRecipientDepartmentId() != null
                ? departmentRepository.findById(invoice.getRecipientDepartmentId()).orElse(null) : null;

        List<UsageCharge> charges = usageChargeRepository.findByInvoiceId(invoice.getId());

        return InvoiceDTO.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .institutionId(invoice.getInstitutionId())
                .institutionName(issuer != null ? issuer.getName() : null)
                .recipientInstitutionId(invoice.getRecipientInstitutionId())
                .recipientInstitutionName(recipientInst != null ? recipientInst.getName() : null)
                .recipientDepartmentId(invoice.getRecipientDepartmentId())
                .recipientDepartmentName(recipientDept != null ? recipientDept.getName() : null)
                .invoiceType(invoice.getInvoiceType())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .subtotal(invoice.getSubtotal())
                .taxRate(invoice.getTaxRate())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .dueDate(invoice.getDueDate())
                .overdue(invoice.isOverdue())
                .status(invoice.getStatus())
                .chargeCount(charges.size())
                .issuedAt(invoice.getIssuedAt())
                .paidAt(invoice.getPaidAt())
                .cancelledAt(invoice.getCancelledAt())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
