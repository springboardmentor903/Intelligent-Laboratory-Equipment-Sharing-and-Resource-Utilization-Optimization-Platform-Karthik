package com.labresource.platform.billing.service;

import com.labresource.platform.billing.entity.UsageCharge;
import com.labresource.platform.billing.model.ChargeStatus;
import com.labresource.platform.billing.model.ChargeType;
import com.labresource.platform.billing.model.ChargesPageResponse;
import com.labresource.platform.billing.model.UsageChargeDTO;
import com.labresource.platform.billing.repository.UsageChargeRepository;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.entity.SharingListing;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.sharing.repository.SharingListingRepository;
import com.labresource.platform.sharing.service.SharingFeeService;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public class UsageChargeService {

    private static final Logger log = LoggerFactory.getLogger(UsageChargeService.class);

    private final UsageChargeRepository usageChargeRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final DepartmentRepository departmentRepository;
    private final SharingListingRepository sharingListingRepository;
    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingFeeService sharingFeeService;

    @Autowired
    public UsageChargeService(
            UsageChargeRepository usageChargeRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            SharingListingRepository sharingListingRepository,
            SharingAgreementRepository sharingAgreementRepository,
            SharingFeeService sharingFeeService) {
        this.usageChargeRepository = usageChargeRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.institutionRepository = institutionRepository;
        this.departmentRepository = departmentRepository;
        this.sharingListingRepository = sharingListingRepository;
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.sharingFeeService = sharingFeeService;
    }

    /**
     * Creates a usage charge record for a completed booking in the same transaction.
     * Rules:
     * - Do NOT create charges for cancelled bookings, No Shows, or zero usage duration.
     * - Calculate charges from actual usage minutes using BigDecimal fee calculation:
     *   (actualMinutes * hourlyRate) / 60, rounded HALF_UP to 2 decimal places at the end.
     * - Three charge types:
     *   INTERNAL: Users belonging to the equipment-owning institution.
     *   EXTERNAL: Users belonging to another institution, priced using the applicable SharingAgreement rate.
     *   UNASSIGNED: Users without an institution; record cost for reporting, but exclude from invoices.
     * - Store snapshot of hourly rate.
     */
    @Transactional
    public UsageCharge createChargeForCompletedBooking(Booking booking, Long actualMinutes) {
        if (booking == null || booking.getId() == null) {
            return null;
        }

        String status = booking.getStatus() != null ? booking.getStatus().toUpperCase() : "";
        if ("CANCELLED".equals(status) || "NO_SHOW".equals(status)) {
            log.debug("Skipping charge for booking {} with status {}", booking.getId(), status);
            return null;
        }

        if (actualMinutes == null || actualMinutes <= 0) {
            log.debug("Skipping charge for booking {} with zero or negative duration: {}", booking.getId(), actualMinutes);
            return null;
        }

        if (usageChargeRepository.existsByBookingId(booking.getId())) {
            return usageChargeRepository.findByBookingId(booking.getId()).orElse(null);
        }

        Equipment equipment = equipmentRepository.findById(booking.getEquipmentId()).orElse(null);
        if (equipment == null) {
            log.warn("Cannot create usage charge: Equipment {} not found for booking {}", booking.getEquipmentId(), booking.getId());
            return null;
        }

        User user = userRepository.findById(booking.getUserId()).orElse(null);
        if (user == null) {
            log.warn("Cannot create usage charge: User {} not found for booking {}", booking.getUserId(), booking.getId());
            return null;
        }

        Long ownerInstitutionId = equipment.getInstitutionId();
        Long userInstitutionId = user.getInstitutionId();

        ChargeType chargeType;
        if (userInstitutionId == null) {
            chargeType = ChargeType.UNASSIGNED;
        } else if (userInstitutionId.equals(ownerInstitutionId)) {
            chargeType = ChargeType.INTERNAL;
        } else {
            chargeType = ChargeType.EXTERNAL;
        }

        BigDecimal hourlyRate = determineHourlyRate(booking, equipment, chargeType);
        BigDecimal amount = sharingFeeService.calculateFee(actualMinutes, hourlyRate);

        UsageCharge charge = UsageCharge.builder()
                .bookingId(booking.getId())
                .equipmentId(equipment.getId())
                .userId(user.getId())
                .institutionId(ownerInstitutionId)
                .userInstitutionId(userInstitutionId)
                .departmentId(user.getDepartmentId())
                .chargeType(chargeType)
                .hourlyRate(hourlyRate)
                .usageMinutes(actualMinutes)
                .amount(amount)
                .status(ChargeStatus.PENDING)
                .sharingAgreementId(booking.getSharingAgreementId())
                .createdAt(LocalDateTime.now())
                .build();

        return usageChargeRepository.save(charge);
    }

    private BigDecimal determineHourlyRate(Booking booking, Equipment equipment, ChargeType chargeType) {
        if (chargeType == ChargeType.EXTERNAL) {
            if (booking.getSharingAgreementId() != null) {
                Optional<SharingAgreement> agreement = sharingAgreementRepository.findById(booking.getSharingAgreementId());
                if (agreement.isPresent() && agreement.get().getFeePerHour() != null) {
                    return agreement.get().getFeePerHour();
                }
            }
            // Fallback to active sharing listing
            Optional<SharingListing> listing = sharingListingRepository.findByEquipmentId(equipment.getId());
            if (listing.isPresent() && listing.get().getFeePerHour() != null) {
                return listing.get().getFeePerHour();
            }
        }

        // INTERNAL or UNASSIGNED (or external fallback)
        if (equipment.getHourlyRate() != null && equipment.getHourlyRate().compareTo(BigDecimal.ZERO) >= 0) {
            return equipment.getHourlyRate();
        }

        Optional<SharingListing> listing = sharingListingRepository.findByEquipmentId(equipment.getId());
        if (listing.isPresent() && listing.get().getFeePerHour() != null) {
            return listing.get().getFeePerHour();
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public ChargesPageResponse getMyCharges(Long userId, LocalDate startDate, LocalDate endDate, ChargeStatus status, Pageable pageable) {
        Specification<UsageCharge> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(LocalTime.MAX)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<UsageCharge> page = usageChargeRepository.findAll(spec, pageable);
        List<UsageCharge> allMatching = usageChargeRepository.findAll(spec);
        BigDecimal totalAmount = allMatching.stream()
                .map(UsageCharge::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<UsageChargeDTO> dtoList = mapToDTOList(page.getContent());

        return ChargesPageResponse.builder()
                .content(dtoList)
                .totalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP))
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public ChargesPageResponse getInstitutionalCharges(
            Long institutionId, Long departmentId, ChargeType chargeType, ChargeStatus status,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {

        Specification<UsageCharge> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("institutionId"), institutionId));
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId));
            }
            if (chargeType != null) {
                predicates.add(cb.equal(root.get("chargeType"), chargeType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(LocalTime.MAX)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<UsageCharge> page = usageChargeRepository.findAll(spec, pageable);
        List<UsageCharge> allMatching = usageChargeRepository.findAll(spec);
        BigDecimal totalAmount = allMatching.stream()
                .map(UsageCharge::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<UsageChargeDTO> dtoList = mapToDTOList(page.getContent());

        return ChargesPageResponse.builder()
                .content(dtoList)
                .totalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP))
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    public List<UsageChargeDTO> mapToDTOList(List<UsageCharge> charges) {
        if (charges == null || charges.isEmpty()) return List.of();

        Set<Long> equipIds = charges.stream().map(UsageCharge::getEquipmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = charges.stream().map(UsageCharge::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> instIds = charges.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.getInstitutionId(), c.getUserInstitutionId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> deptIds = charges.stream().map(UsageCharge::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Equipment> equipMap = equipmentRepository.findAllById(equipIds).stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Institution> instMap = institutionRepository.findAllById(instIds).stream()
                .collect(Collectors.toMap(Institution::getId, i -> i));
        Map<Long, Department> deptMap = departmentRepository.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, d -> d));

        return charges.stream().map(c -> {
            Equipment eq = equipMap.get(c.getEquipmentId());
            User u = userMap.get(c.getUserId());
            Institution ownerInst = instMap.get(c.getInstitutionId());
            Institution userInst = c.getUserInstitutionId() != null ? instMap.get(c.getUserInstitutionId()) : null;
            Department dept = c.getDepartmentId() != null ? deptMap.get(c.getDepartmentId()) : null;

            BigDecimal usageHours = BigDecimal.valueOf(c.getUsageMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            return UsageChargeDTO.builder()
                    .id(c.getId())
                    .bookingId(c.getBookingId())
                    .equipmentId(c.getEquipmentId())
                    .equipmentName(eq != null ? eq.getName() : "Unknown Equipment")
                    .assetTag(eq != null ? eq.getAssetTag() : null)
                    .userId(c.getUserId())
                    .userName(u != null ? u.getFullName() : "Unknown User")
                    .userEmail(u != null ? u.getEmail() : null)
                    .institutionId(c.getInstitutionId())
                    .institutionName(ownerInst != null ? ownerInst.getName() : null)
                    .userInstitutionId(c.getUserInstitutionId())
                    .userInstitutionName(userInst != null ? userInst.getName() : null)
                    .departmentId(c.getDepartmentId())
                    .departmentName(dept != null ? dept.getName() : "Unassigned / None")
                    .chargeType(c.getChargeType())
                    .hourlyRate(c.getHourlyRate())
                    .usageMinutes(c.getUsageMinutes())
                    .usageHours(usageHours)
                    .amount(c.getAmount())
                    .status(c.getStatus())
                    .invoiceId(c.getInvoiceId())
                    .sharingAgreementId(c.getSharingAgreementId())
                    .createdAt(c.getCreatedAt())
                    .build();
        }).toList();
    }
}
