package com.labresource.platform.sharing;

import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.entity.EquipmentOperatingHours;
import com.labresource.platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.labresource.platform.sharing.entity.SharingAccessGrant;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.entity.SharingListing;
import com.labresource.platform.sharing.repository.SharingAccessGrantRepository;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.sharing.repository.SharingListingRepository;
import com.labresource.platform.sharing.service.ExternalBookingValidator;
import com.labresource.platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalBookingValidatorTests {

    @Mock private SharingAgreementRepository agreements;
    @Mock private SharingAccessGrantRepository grants;
    @Mock private SharingListingRepository listings;
    @Mock private EquipmentOperatingHoursRepository operatingHours;
    @Mock private BookingRepository bookings;

    private ExternalBookingValidator validator;

    private User externalUser;
    private User internalUser;
    private Equipment equipment;
    private SharingListing listing;
    private SharingAgreement agreement;
    private SharingAccessGrant grant;

    @BeforeEach
    void setUp() {
        validator = new ExternalBookingValidator(agreements, grants, listings, operatingHours, bookings);

        externalUser = new User();
        externalUser.setId(10L);
        externalUser.setInstitutionId(2L);

        internalUser = new User();
        internalUser.setId(20L);
        internalUser.setInstitutionId(1L);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setInstitutionId(1L);
        equipment.setQuantity(1);

        listing = new SharingListing();
        listing.setEquipmentId(100L);
        listing.setActive(true);
        listing.setFeePerHour(new BigDecimal("100.00"));
        listing.setExternalSharePercentage(new BigDecimal("50.00"));
        listing.setMinimumNoticeHours(24);
        listing.setMaximumBookingHours(6);

        agreement = new SharingAgreement();
        agreement.setId(55L);
        agreement.setEquipmentId(100L);
        agreement.setOwnerInstitutionId(1L);
        agreement.setPartnerInstitutionId(2L);
        agreement.setStatus("ACTIVE");
        agreement.setStartDate(LocalDateTime.now().minusDays(10));
        agreement.setEndDate(LocalDateTime.now().plusMonths(3));
        agreement.setAllowedStartTime(LocalTime.of(9, 0));
        agreement.setAllowedEndTime(LocalTime.of(17, 0));

        grant = new SharingAccessGrant();
        grant.setId(77L);
        grant.setAgreementId(55L);
        grant.setUserId(10L);
        grant.setStatus("ACTIVE");

        List<EquipmentOperatingHours> hours = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            EquipmentOperatingHours h = new EquipmentOperatingHours();
            h.setEquipmentId(100L);
            h.setDayOfWeek(day);
            h.setOpenTime(LocalTime.of(9, 0));
            h.setCloseTime(LocalTime.of(17, 0));
            h.setIsClosed(day > 5); // Sat/Sun closed
            hours.add(h);
        }
        when(operatingHours.findByEquipmentIdOrderByDayOfWeekAsc(100L)).thenReturn(hours);
    }

    @Test
    void internalUserReturnsNullBypassingExternalValidation() {
        Long agreementId = validator.validate(internalUser, equipment, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));
        assertThat(agreementId).isNull();
    }

    @Test
    void validMultiHourBookingWithinAllowedDaytimeHoursSucceeds() {
        when(listings.findByEquipmentId(100L)).thenReturn(Optional.of(listing));
        when(grants.findByUserId(10L)).thenReturn(List.of(grant));
        when(agreements.findById(55L)).thenReturn(Optional.of(agreement));
        when(bookings.findOverlappingBookingsForEquipment(eq(100L), any(), any())).thenReturn(List.of());

        // Booking on Wednesday, 10:00 to 14:00 (4 hours, >=24h in advance, within 09:00-17:00)
        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(4);

        Long agreementId = validator.validate(externalUser, equipment, start, end);
        assertThat(agreementId).isEqualTo(55L);
    }

    @Test
    void bookingExceedingMaxDurationThrowsException() {
        when(listings.findByEquipmentId(100L)).thenReturn(Optional.of(listing));
        when(grants.findByUserId(10L)).thenReturn(List.of(grant));
        when(agreements.findById(55L)).thenReturn(Optional.of(agreement));

        // 7 hours booking exceeds maximum 6 hours
        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(7);

        assertThatThrownBy(() -> validator.validate(externalUser, equipment, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum duration");
    }

    @Test
    void bookingViolatingMinimumNoticePeriodThrowsException() {
        when(listings.findByEquipmentId(100L)).thenReturn(Optional.of(listing));
        when(grants.findByUserId(10L)).thenReturn(List.of(grant));
        when(agreements.findById(55L)).thenReturn(Optional.of(agreement));

        // Start only 2 hours from now, but 24 hours notice required
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(2);

        assertThatThrownBy(() -> validator.validate(externalUser, equipment, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum notice period");
    }

    @Test
    void bookingOutsideAllowedHoursThrowsException() {
        when(listings.findByEquipmentId(100L)).thenReturn(Optional.of(listing));
        when(grants.findByUserId(10L)).thenReturn(List.of(grant));
        when(agreements.findById(55L)).thenReturn(Optional.of(agreement));

        // Start at 08:00 (allowed starts at 09:00)
        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(8).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        assertThatThrownBy(() -> validator.validate(externalUser, equipment, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the agreement allowed hours");
    }
}
