package com.labresource.platform.sharing;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.entity.SharingListing;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.sharing.repository.SharingListingRepository;
import com.labresource.platform.sharing.service.SharingAnalyticsService;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharingAnalyticsServiceTests {
    @Mock private SharingAgreementRepository agreementRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UtilizationRepository utilizationRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private SharingListingRepository listingRepository;
    @Mock private UserRepository userRepository;

    private SharingAnalyticsService service;
    private SharingAgreement agreement;
    private Booking booking;
    private Utilization externalUsage;
    private Utilization internalUsage;
    private Equipment equipment;
    private SharingListing listing;

    @BeforeEach
    void setUp() {
        service = new SharingAnalyticsService(agreementRepository, bookingRepository, utilizationRepository,
                equipmentRepository, institutionRepository, listingRepository, userRepository);

        agreement = new SharingAgreement();
        agreement.setId(5L); agreement.setEquipmentId(9L); agreement.setOwnerInstitutionId(1L);
        agreement.setPartnerInstitutionId(2L); agreement.setFeePerHour(new BigDecimal("120.00"));
        agreement.setStatus("ACTIVE"); agreement.setEndDate(LocalDateTime.now().plusDays(10));

        booking = new Booking(); booking.setId(20L); booking.setEquipmentId(9L); booking.setSharingAgreementId(5L);
        booking.setStartTime(LocalDateTime.of(2026, 1, 10, 10, 0)); booking.setEndTime(LocalDateTime.of(2026, 1, 10, 12, 0));
        booking.setStatus("COMPLETED");

        externalUsage = new Utilization(); externalUsage.setId(30L); externalUsage.setEquipmentId(9L);
        externalUsage.setBookingId(20L); externalUsage.setUsageType("SHARED");
        externalUsage.setStartTime(LocalDateTime.of(2026, 1, 10, 10, 0));
        externalUsage.setEndTime(LocalDateTime.of(2026, 1, 10, 11, 0));

        internalUsage = new Utilization(); internalUsage.setId(31L); internalUsage.setEquipmentId(9L);
        internalUsage.setUsageType("BOOKING"); internalUsage.setStartTime(LocalDateTime.of(2026, 1, 10, 11, 0));
        internalUsage.setEndTime(LocalDateTime.of(2026, 1, 10, 11, 30));

        equipment = new Equipment(); equipment.setId(9L); equipment.setName("Electron Microscope");
        equipment.setAssetTag("MIC-009"); equipment.setInstitutionId(1L);
        listing = new SharingListing(); listing.setEquipmentId(9L); listing.setExternalSharePercentage(new BigDecimal("40.00"));

        Institution owner = new Institution(); owner.setId(1L); owner.setName("Owner University");
        Institution partner = new Institution(); partner.setId(2L); partner.setName("Partner University");
        when(agreementRepository.findByOwnerInstitutionId(1L)).thenReturn(List.of(agreement));
        when(agreementRepository.findByPartnerInstitutionId(1L)).thenReturn(List.of());
        when(bookingRepository.findBySharingAgreementIdIn(anyList())).thenReturn(List.of(booking));
        when(utilizationRepository.findByBookingIdIn(anyList())).thenReturn(List.of(externalUsage));
        when(utilizationRepository.findByEquipmentIdIn(anyList())).thenReturn(List.of(externalUsage, internalUsage));
        when(equipmentRepository.findAllById(anyList())).thenReturn(List.of(equipment));
        when(institutionRepository.findAllById(anyList())).thenReturn(List.of(owner, partner));
        when(listingRepository.findByEquipmentIdIn(anyList())).thenReturn(List.of(listing));
    }

    @Test
    void summaryUsesActualUsageLogsForHoursAndFees() {
        Map<String, Object> summary = service.summary(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(summary.get("hoursProvided")).isEqualTo(1.0);
        assertThat(summary.get("feesEarned")).isEqualTo(new BigDecimal("120.00"));
        assertThat(summary.get("bookingCount")).isEqualTo(1L);
        assertThat(summary.get("noShowRate")).isEqualTo(0.0);
        assertThat(summary.get("externalShareOfUsage")).isEqualTo(66.67);
        assertThat(summary.get("expectedFees")).isEqualTo(new BigDecimal("0.00"));
    }
}
