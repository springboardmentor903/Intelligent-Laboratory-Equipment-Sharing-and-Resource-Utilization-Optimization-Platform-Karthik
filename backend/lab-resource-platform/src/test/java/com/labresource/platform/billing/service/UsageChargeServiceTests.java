package com.labresource.platform.billing.service;

import com.labresource.platform.billing.entity.UsageCharge;
import com.labresource.platform.billing.model.ChargeStatus;
import com.labresource.platform.billing.model.ChargeType;
import com.labresource.platform.billing.model.ChargesPageResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageChargeServiceTests {

    @Mock
    private UsageChargeRepository usageChargeRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InstitutionRepository institutionRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private SharingListingRepository sharingListingRepository;
    @Mock
    private SharingAgreementRepository sharingAgreementRepository;

    private SharingFeeService sharingFeeService;
    private UsageChargeService usageChargeService;

    @BeforeEach
    void setUp() {
        sharingFeeService = new SharingFeeService();
        usageChargeService = new UsageChargeService(
                usageChargeRepository,
                equipmentRepository,
                userRepository,
                institutionRepository,
                departmentRepository,
                sharingListingRepository,
                sharingAgreementRepository,
                sharingFeeService
        );
    }

    @Test
    @DisplayName("Should create INTERNAL charge for same institution booking with HALF_UP rounding")
    void createsInternalChargeWithExactHalfUpRounding() {
        Booking booking = new Booking();
        booking.setId(101L);
        booking.setEquipmentId(10L);
        booking.setUserId(20L);
        booking.setStatus("COMPLETED");

        Equipment equipment = new Equipment();
        equipment.setId(10L);
        equipment.setInstitutionId(1L);
        equipment.setHourlyRate(new BigDecimal("150.00"));

        User user = new User();
        user.setId(20L);
        user.setInstitutionId(1L);
        user.setDepartmentId(5L);

        when(usageChargeRepository.existsByBookingId(101L)).thenReturn(false);
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(usageChargeRepository.save(any(UsageCharge.class))).thenAnswer(i -> i.getArgument(0));

        // 95 minutes at 150.00/hr = (95 * 150) / 60 = 237.50
        UsageCharge charge = usageChargeService.createChargeForCompletedBooking(booking, 95L);

        assertThat(charge).isNotNull();
        assertThat(charge.getChargeType()).isEqualTo(ChargeType.INTERNAL);
        assertThat(charge.getHourlyRate()).isEqualByComparingTo("150.00");
        assertThat(charge.getUsageMinutes()).isEqualTo(95L);
        assertThat(charge.getAmount()).isEqualByComparingTo("237.50");
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(charge.getInstitutionId()).isEqualTo(1L);
        assertThat(charge.getUserInstitutionId()).isEqualTo(1L);
        assertThat(charge.getDepartmentId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should create EXTERNAL charge using SharingAgreement feePerHour")
    void createsExternalChargeUsingSharingAgreementRate() {
        Booking booking = new Booking();
        booking.setId(102L);
        booking.setEquipmentId(10L);
        booking.setUserId(30L);
        booking.setSharingAgreementId(55L);
        booking.setStatus("COMPLETED");

        Equipment equipment = new Equipment();
        equipment.setId(10L);
        equipment.setInstitutionId(1L);
        equipment.setHourlyRate(new BigDecimal("100.00")); // Internal rate

        User user = new User();
        user.setId(30L);
        user.setInstitutionId(2L); // Different institution

        SharingAgreement agreement = new SharingAgreement();
        agreement.setId(55L);
        agreement.setFeePerHour(new BigDecimal("350.00")); // External agreement rate

        when(usageChargeRepository.existsByBookingId(102L)).thenReturn(false);
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));
        when(sharingAgreementRepository.findById(55L)).thenReturn(Optional.of(agreement));
        when(usageChargeRepository.save(any(UsageCharge.class))).thenAnswer(i -> i.getArgument(0));

        // 45 minutes at 350.00/hr = (45 * 350) / 60 = 262.50
        UsageCharge charge = usageChargeService.createChargeForCompletedBooking(booking, 45L);

        assertThat(charge).isNotNull();
        assertThat(charge.getChargeType()).isEqualTo(ChargeType.EXTERNAL);
        assertThat(charge.getHourlyRate()).isEqualByComparingTo("350.00");
        assertThat(charge.getAmount()).isEqualByComparingTo("262.50");
        assertThat(charge.getUserInstitutionId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should create UNASSIGNED charge for user without institution")
    void createsUnassignedChargeForUserWithoutInstitution() {
        Booking booking = new Booking();
        booking.setId(103L);
        booking.setEquipmentId(10L);
        booking.setUserId(40L);
        booking.setStatus("COMPLETED");

        Equipment equipment = new Equipment();
        equipment.setId(10L);
        equipment.setInstitutionId(1L);
        equipment.setHourlyRate(new BigDecimal("200.00"));

        User user = new User();
        user.setId(40L);
        user.setInstitutionId(null); // Unassigned

        when(usageChargeRepository.existsByBookingId(103L)).thenReturn(false);
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(userRepository.findById(40L)).thenReturn(Optional.of(user));
        when(usageChargeRepository.save(any(UsageCharge.class))).thenAnswer(i -> i.getArgument(0));

        // 60 minutes at 200.00/hr = 200.00
        UsageCharge charge = usageChargeService.createChargeForCompletedBooking(booking, 60L);

        assertThat(charge).isNotNull();
        assertThat(charge.getChargeType()).isEqualTo(ChargeType.UNASSIGNED);
        assertThat(charge.getUserInstitutionId()).isNull();
        assertThat(charge.getAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Should NOT create charge for cancelled bookings, no shows, or zero duration")
    void doesNotCreateChargeForCancelledNoShowOrZeroDuration() {
        Booking cancelled = new Booking();
        cancelled.setId(201L);
        cancelled.setStatus("CANCELLED");
        assertThat(usageChargeService.createChargeForCompletedBooking(cancelled, 60L)).isNull();

        Booking noShow = new Booking();
        noShow.setId(202L);
        noShow.setStatus("NO_SHOW");
        assertThat(usageChargeService.createChargeForCompletedBooking(noShow, 60L)).isNull();

        Booking zeroDuration = new Booking();
        zeroDuration.setId(203L);
        zeroDuration.setStatus("COMPLETED");
        assertThat(usageChargeService.createChargeForCompletedBooking(zeroDuration, 0L)).isNull();
        assertThat(usageChargeService.createChargeForCompletedBooking(zeroDuration, -5L)).isNull();

        verifyNoInteractions(usageChargeRepository);
    }

    @Test
    @DisplayName("Should be idempotent and avoid creating duplicate charges for same booking")
    void avoidsDuplicateChargesForSameBooking() {
        Booking booking = new Booking();
        booking.setId(301L);
        booking.setStatus("COMPLETED");

        UsageCharge existing = UsageCharge.builder().id(999L).bookingId(301L).build();
        when(usageChargeRepository.existsByBookingId(301L)).thenReturn(true);
        when(usageChargeRepository.findByBookingId(301L)).thenReturn(Optional.of(existing));

        UsageCharge result = usageChargeService.createChargeForCompletedBooking(booking, 60L);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(999L);
        verify(usageChargeRepository, never()).save(any(UsageCharge.class));
    }
}
