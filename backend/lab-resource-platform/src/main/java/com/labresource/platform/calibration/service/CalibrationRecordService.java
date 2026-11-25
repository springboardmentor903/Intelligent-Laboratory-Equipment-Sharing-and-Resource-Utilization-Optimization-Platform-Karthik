package com.labresource.platform.calibration.service;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.model.CalibrationOverviewItem;
import com.labresource.platform.calibration.model.CalibrationReminderItem;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CalibrationRecordService {

    private static final Logger log = LoggerFactory.getLogger(CalibrationRecordService.class);

    private static final List<String> VALID_RESULTS = List.of("PASS", "FAIL", "CONDITIONAL");

    private final CalibrationRecordRepository calibrationRecordRepository;
    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ZoneId platformZoneId;

    public CalibrationRecordService(
            CalibrationRecordRepository calibrationRecordRepository,
            EquipmentRepository equipmentRepository) {
        this(calibrationRecordRepository, equipmentRepository, null, null, null, "Asia/Kolkata");
    }

    @Autowired
    public CalibrationRecordService(
            CalibrationRecordRepository calibrationRecordRepository,
            EquipmentRepository equipmentRepository,
            WorkOrderRepository workOrderRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.equipmentRepository = equipmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        this.platformZoneId = zone;
    }

    public List<CalibrationRecord> getByEquipment(Long equipmentId) {
        requireEquipment(equipmentId);
        return calibrationRecordRepository
                .findByEquipment_IdOrderByCalibrationDateDesc(equipmentId);
    }

    @Transactional
    public CalibrationRecord create(Long equipmentId, CalibrationRecord record) {
        Equipment equipment = requireEquipment(equipmentId);

        if (record.getCalibrationDate() == null || record.getNextDueDate() == null) {
            throw new IllegalArgumentException("Calibration date and next due date are required.");
        }
        if (!record.getNextDueDate().isAfter(record.getCalibrationDate())) {
            throw new IllegalArgumentException("Next due date must be after the calibration date.");
        }
        if (record.getResult() == null || record.getResult().isBlank()) {
            throw new IllegalArgumentException("Calibration result is required.");
        }

        String result = record.getResult().trim().toUpperCase(Locale.ROOT);
        if (!VALID_RESULTS.contains(result)) {
            throw new IllegalArgumentException("Calibration result must be PASS, FAIL, or CONDITIONAL.");
        }
        if (record.getNotes() != null && record.getNotes().length() > 1000) {
            throw new IllegalArgumentException("Calibration notes must be 1000 characters or fewer.");
        }

        record.setId(null);
        record.setEquipment(equipment);
        record.setResult(result);
        record.setStatus("COMPLETED");
        record.setCreatedAt(LocalDateTime.now());
        if (record.getNotes() != null) {
            record.setNotes(record.getNotes().trim());
        }
        return calibrationRecordRepository.save(record);
    }

    public List<CalibrationRecord> getAll(Long institutionId) {
        if (institutionId != null) {
            return calibrationRecordRepository.findByEquipment_InstitutionIdOrderByNextDueDateAsc(institutionId);
        }
        return calibrationRecordRepository.findAll();
    }

    public List<CalibrationRecord> getByTechnician(Long technicianId) {
        if (technicianId == null) return List.of();
        return calibrationRecordRepository.findByAssignedToOrderByNextDueDateAsc(technicianId);
    }

    public List<CalibrationRecord> getDueCalibrations(Long institutionId, int daysAhead) {
        LocalDate cutoff = LocalDate.now().plusDays(daysAhead > 0 ? daysAhead : 30);
        List<CalibrationRecord> records = getAll(institutionId);
        return records.stream()
                .filter(r -> r.getNextDueDate() != null && !r.getNextDueDate().isAfter(cutoff))
                .toList();
    }

    @Transactional
    public CalibrationRecord schedule(Long equipmentId, CalibrationRecord record) {
        Equipment equipment = requireEquipment(equipmentId);

        LocalDate dueDate = record.getNextDueDate() != null ? record.getNextDueDate() : record.getScheduledDate();
        if (dueDate == null) {
            throw new IllegalArgumentException("A scheduled date or next due date is required.");
        }

        record.setId(null);
        record.setEquipment(equipment);
        record.setNextDueDate(dueDate);
        record.setScheduledDate(record.getScheduledDate() != null ? record.getScheduledDate() : dueDate);
        record.setStatus("SCHEDULED");
        record.setResult(null);
        record.setCalibrationDate(null);
        record.setCreatedAt(LocalDateTime.now());
        if (record.getNotes() != null) {
            record.setNotes(record.getNotes().trim());
        }
        return calibrationRecordRepository.save(record);
    }

    @Transactional
    public CalibrationRecord complete(Long id, LocalDate calibrationDate, String result,
                                      LocalDate nextDueDate, String notes, String certificateNumber) {
        CalibrationRecord record = calibrationRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Calibration record not found with ID: " + id));

        LocalDate completedDate = calibrationDate != null ? calibrationDate : LocalDate.now();

        if (nextDueDate == null) {
            throw new IllegalArgumentException("Next due date is required.");
        }
        if (!nextDueDate.isAfter(completedDate)) {
            throw new IllegalArgumentException("Next due date must be after the calibration date.");
        }
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("Calibration result is required.");
        }

        String normalizedResult = result.trim().toUpperCase(Locale.ROOT);
        if (!VALID_RESULTS.contains(normalizedResult)) {
            throw new IllegalArgumentException("Calibration result must be PASS, FAIL, or CONDITIONAL.");
        }

        record.setCalibrationDate(completedDate);
        record.setNextDueDate(nextDueDate);
        record.setResult(normalizedResult);
        record.setStatus("COMPLETED");
        if (notes != null && !notes.isBlank()) {
            record.setNotes(notes.trim());
        }
        if (certificateNumber != null && !certificateNumber.isBlank()) {
            record.setCertificateNumber(certificateNumber.trim());
        }

        return calibrationRecordRepository.save(record);
    }

    public Map<String, Object> getSummary(Long institutionId) {
        List<CalibrationRecord> records = getAll(institutionId);
        LocalDate today = LocalDate.now();
        LocalDate dueSoonCutoff = today.plusDays(30);

        long total = records.size();
        long passed = records.stream().filter(r -> "PASS".equalsIgnoreCase(r.getResult())).count();
        long failed = records.stream().filter(r -> "FAIL".equalsIgnoreCase(r.getResult())).count();
        long conditional = records.stream().filter(r -> "CONDITIONAL".equalsIgnoreCase(r.getResult())).count();
        long scheduled = records.stream().filter(r -> "SCHEDULED".equalsIgnoreCase(r.getStatus())).count();
        long overdue = records.stream().filter(r -> r.getNextDueDate() != null && r.getNextDueDate().isBefore(today)).count();
        long dueSoon = records.stream().filter(r -> r.getNextDueDate() != null
                && !r.getNextDueDate().isBefore(today)
                && !r.getNextDueDate().isAfter(dueSoonCutoff)).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("conditional", conditional);
        summary.put("scheduled", scheduled);
        summary.put("overdue", overdue);
        summary.put("dueSoon", dueSoon);
        return summary;
    }

    /**
     * Calibration Overview for all equipment within an institution.
     * Dynamically calculates calibration state as VALID, DUE_SOON, EXPIRED, or NONE.
     * Supports filtering by department, calibration state, and keyword, along with sorting and pagination.
     */
    public Page<CalibrationOverviewItem> getCalibrationOverview(
            Long institutionId,
            Long departmentId,
            String stateFilter,
            String keyword,
            Pageable pageable) {

        List<Equipment> equipmentList;
        if (institutionId != null) {
            equipmentList = equipmentRepository.findByInstitutionId(institutionId);
        } else {
            equipmentList = equipmentRepository.findAll();
        }

        // Cache departments for fast lookup
        Map<Long, String> departmentNames = new HashMap<>();
        departmentRepository.findAll().forEach(d -> departmentNames.put(d.getId(), d.getName()));

        LocalDate today = LocalDate.now();

        List<CalibrationOverviewItem> items = equipmentList.stream()
                .map(eq -> {
                    // Latest calibration record
                    Optional<CalibrationRecord> latestRecordOpt = calibrationRecordRepository
                            .findFirstByEquipment_IdOrderByCreatedAtDesc(eq.getId());
                    if (latestRecordOpt.isEmpty()) {
                        latestRecordOpt = calibrationRecordRepository
                                .findFirstByEquipment_IdOrderByCalibrationDateDesc(eq.getId());
                    }

                    CalibrationRecord rec = latestRecordOpt.orElse(null);

                    // Check active calibration work order
                    List<WorkOrder> activeWos = workOrderRepository
                            .findByEquipmentIdAndWorkOrderTypeAndStatusNotIn(
                                    eq.getId(), "CALIBRATION", List.of("COMPLETED", "CANCELLED")
                            );
                    WorkOrder activeWo = activeWos.isEmpty() ? null : activeWos.get(0);

                    LocalDate latestDate = rec != null ? rec.getCalibrationDate() : null;
                    LocalDate nextDue = rec != null ? rec.getNextDueDate() : null;

                    Long daysUntilDue = null;
                    String state;
                    if (rec == null || nextDue == null) {
                        state = "NONE";
                    } else {
                        daysUntilDue = ChronoUnit.DAYS.between(today, nextDue);
                        if (nextDue.isBefore(today)) {
                            state = "EXPIRED";
                        } else if (!nextDue.isAfter(today.plusDays(30))) {
                            state = "DUE_SOON";
                        } else {
                            state = "VALID";
                        }
                    }

                    return CalibrationOverviewItem.builder()
                            .equipmentId(eq.getId())
                            .equipmentName(eq.getName())
                            .assetTag(eq.getAssetTag())
                            .departmentId(eq.getDepartmentId())
                            .departmentName(eq.getDepartmentId() != null ? departmentNames.get(eq.getDepartmentId()) : null)
                            .institutionId(eq.getInstitutionId())
                            .latestCalibrationDate(latestDate)
                            .nextDueDate(nextDue)
                            .daysUntilDue(daysUntilDue)
                            .calibrationState(state)
                            .latestResult(rec != null ? rec.getResult() : null)
                            .latestCertificateNumber(rec != null ? rec.getCertificateNumber() : null)
                            .latestCertificateDocumentId(rec != null ? rec.getCertificateDocumentId() : null)
                            .latestCertificateReference(rec != null ? rec.getCertificateReference() : null)
                            .activeWorkOrderId(activeWo != null ? activeWo.getId() : null)
                            .activeWorkOrderNumber(activeWo != null ? activeWo.getWorkOrderNumber() : null)
                            .activeWorkOrderStatus(activeWo != null ? activeWo.getStatus() : null)
                            .build();
                })
                .map(CalibrationOverviewItem.class::cast)
                .toList();

        // Apply filters
        List<CalibrationOverviewItem> filtered = items.stream()
                .filter(item -> {
                    if (departmentId != null && !Objects.equals(item.getDepartmentId(), departmentId)) {
                        return false;
                    }
                    if (stateFilter != null && !stateFilter.isBlank() && !"ALL".equalsIgnoreCase(stateFilter)) {
                        if (!stateFilter.equalsIgnoreCase(item.getCalibrationState())) {
                            return false;
                        }
                    }
                    if (keyword != null && !keyword.isBlank()) {
                        String kw = keyword.trim().toLowerCase(Locale.ROOT);
                        boolean nameMatch = item.getEquipmentName() != null && item.getEquipmentName().toLowerCase(Locale.ROOT).contains(kw);
                        boolean tagMatch = item.getAssetTag() != null && item.getAssetTag().toLowerCase(Locale.ROOT).contains(kw);
                        if (!nameMatch && !tagMatch) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing((CalibrationOverviewItem i) -> {
                    // Default urgency sorting: EXPIRED (0), DUE_SOON (1), VALID (2), NONE (3)
                    return switch (i.getCalibrationState()) {
                        case "EXPIRED" -> 0;
                        case "DUE_SOON" -> 1;
                        case "VALID" -> 2;
                        default -> 3;
                    };
                }).thenComparing((CalibrationOverviewItem i) -> i.getNextDueDate() != null ? i.getNextDueDate() : LocalDate.MAX))
                .toList();

        if (pageable != null && pageable.isPaged()) {
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filtered.size());
            List<CalibrationOverviewItem> subList = start > filtered.size() ? List.of() : filtered.subList(start, end);
            return new PageImpl<>(subList, pageable, filtered.size());
        }

        return new PageImpl<>(filtered, pageable != null ? pageable : Pageable.unpaged(), filtered.size());
    }

    /**
     * Renewal Reminders API: Returns equipment that is due soon or expired (ordered by urgency).
     */
    public List<CalibrationReminderItem> getCalibrationReminders(Long institutionId) {
        Page<CalibrationOverviewItem> overview = getCalibrationOverview(
                institutionId, null, null, null, Pageable.unpaged()
        );

        LocalDate today = LocalDate.now();

        return overview.getContent().stream()
                .filter(item -> "EXPIRED".equals(item.getCalibrationState()) || "DUE_SOON".equals(item.getCalibrationState()))
                .sorted((a, b) -> {
                    // EXPIRED first, then DUE_SOON
                    boolean aExpired = "EXPIRED".equals(a.getCalibrationState());
                    boolean bExpired = "EXPIRED".equals(b.getCalibrationState());
                    if (aExpired && !bExpired) return -1;
                    if (!aExpired && bExpired) return 1;

                    // Within same state, closest due date first
                    LocalDate aDue = a.getNextDueDate() != null ? a.getNextDueDate() : LocalDate.MAX;
                    LocalDate bDue = b.getNextDueDate() != null ? b.getNextDueDate() : LocalDate.MAX;
                    return aDue.compareTo(bDue);
                })
                .map(item -> CalibrationReminderItem.builder()
                        .equipmentId(item.getEquipmentId())
                        .equipmentName(item.getEquipmentName())
                        .assetTag(item.getAssetTag())
                        .departmentId(item.getDepartmentId())
                        .departmentName(item.getDepartmentName())
                        .latestCalibrationDate(item.getLatestCalibrationDate())
                        .nextDueDate(item.getNextDueDate())
                        .daysUntilDue(item.getDaysUntilDue())
                        .calibrationState(item.getCalibrationState())
                        .activeWorkOrderId(item.getActiveWorkOrderId())
                        .activeWorkOrderNumber(item.getActiveWorkOrderNumber())
                        .activeWorkOrderStatus(item.getActiveWorkOrderStatus())
                        .build()
                )
                .toList();
    }

    /**
     * Daily scheduled job at 1:15 AM (0 15 1 * * *) in platform timezone.
     * Automatically creates CALIBRATION work orders when equipment is due soon or overdue,
     * provided no active calibration work order already exists.
     */
    @Scheduled(cron = "${app.calibration.schedule-cron:0 15 1 * * *}", zone = "${app.timezone:Asia/Kolkata}")
    @Transactional
    public void runDailyCalibrationRenewalJob() {
        LocalDate today = LocalDate.now(platformZoneId);
        log.info("Running daily calibration renewal job at 1:15 AM for date {}", today);

        List<CalibrationReminderItem> reminders = getCalibrationReminders(null);
        int createdCount = 0;

        for (CalibrationReminderItem item : reminders) {
            // Check if active calibration work order already exists
            boolean hasActive = workOrderRepository.existsByEquipmentIdAndWorkOrderTypeAndStatusNotIn(
                    item.getEquipmentId(), "CALIBRATION", List.of("COMPLETED", "CANCELLED")
            );

            if (!hasActive) {
                WorkOrder wo = new WorkOrder();
                wo.setWorkOrderNumber(generateNextWorkOrderNumber());
                wo.setEquipmentId(item.getEquipmentId());
                wo.setWorkOrderType("CALIBRATION");
                wo.setTitle("Calibration: " + item.getEquipmentName());
                wo.setDescription("Scheduled calibration renewal for " + item.getEquipmentName()
                        + " (" + item.getAssetTag() + "). Next due date: " + item.getNextDueDate());
                wo.setPriority("EXPIRED".equals(item.getCalibrationState()) ? "HIGH" : "MEDIUM");
                wo.setStatus("OPEN");
                wo.setScheduledDate(item.getNextDueDate() != null ? item.getNextDueDate().atTime(9, 0) : today.atTime(9, 0));
                wo.setCreatedAt(LocalDateTime.now());
                wo.setUpdatedAt(LocalDateTime.now());

                workOrderRepository.save(wo);
                createdCount++;
                log.info("Created calibration work order {} for equipment {} (state: {})",
                        wo.getWorkOrderNumber(), item.getEquipmentId(), item.getCalibrationState());
            }
        }

        log.info("Daily calibration renewal job finished. Created {} calibration work orders.", createdCount);
    }

    public synchronized String generateNextWorkOrderNumber() {
        int year = LocalDate.now(platformZoneId).getYear();
        String prefix = "WO-" + year + "-";
        Optional<String> maxWo = workOrderRepository.findMaxWorkOrderNumber(prefix);
        long nextSeq = 1;
        if (maxWo.isPresent()) {
            String maxStr = maxWo.get();
            try {
                String seqPart = maxStr.substring(prefix.length());
                nextSeq = Long.parseLong(seqPart) + 1;
            } catch (Exception ignored) {}
        }
        return String.format("%s%06d", prefix, nextSeq);
    }

    private Equipment requireEquipment(Long equipmentId) {
        if (equipmentId == null) {
            throw new IllegalArgumentException("Equipment ID is required.");
        }
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found with ID: " + equipmentId));
    }
}
