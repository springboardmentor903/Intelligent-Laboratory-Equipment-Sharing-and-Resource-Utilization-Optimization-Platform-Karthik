package com.labresource.platform.booking.waitlist;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.service.BookingService.BookingAvailability;
import com.labresource.platform.booking.service.BookingService;
import com.labresource.platform.booking.waitlist.dto.RecurringBookingRequest;
import com.labresource.platform.booking.waitlist.dto.RecurringBookingResponse;
import com.labresource.platform.booking.waitlist.dto.SchedulingRecommendationDto;
import com.labresource.platform.booking.waitlist.dto.WaitlistRequest;
import com.labresource.platform.booking.waitlist.entity.WaitlistEntry;
import com.labresource.platform.booking.waitlist.repository.WaitlistRepository;
import com.labresource.platform.booking.waitlist.service.RecurringBookingService;
import com.labresource.platform.booking.waitlist.service.WaitlistService;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaitlistServiceTests {

    @Mock private WaitlistRepository waitlistRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private com.labresource.platform.user.repository.UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingService bookingService;

    private WaitlistService waitlistService;
    private RecurringBookingService recurringBookingService;
    private Equipment microscope;

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(waitlistRepository, equipmentRepository, bookingRepository, bookingService, userRepository);
        recurringBookingService = new RecurringBookingService(bookingService, bookingRepository, equipmentRepository, userRepository);

        microscope = new Equipment();
        microscope.setId(5L);
        microscope.setName("Optical Microscope");
        microscope.setInstitutionId(1L);
        microscope.setCategory("Microscopy");
        microscope.setQuantity(1);

        lenient().when(bookingService.getAvailability(any(), any(), any()))
                .thenReturn(new BookingAvailability(0L, null, null, false, 0, 0));
        lenient().when(equipmentRepository.findByIdForBooking(any()))
                .thenReturn(Optional.of(microscope));
        lenient().when(equipmentRepository.findById(5L))
                .thenReturn(Optional.of(microscope));
        lenient().when(waitlistRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void userCanJoinWaitlistWhenEquipmentIsBusy() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(microscope));
        when(waitlistRepository.existsByUserIdAndEquipmentIdAndStatus(10L, 5L, "WAITING")).thenReturn(false);
        when(waitlistRepository.countByEquipmentIdAndStatus(5L, "WAITING")).thenReturn(2L);
        when(waitlistRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WaitlistRequest req = new WaitlistRequest(
                5L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "Need for experiment"
        );

        WaitlistEntry entry = waitlistService.joinWaitlist(10L, req);
        assertThat(entry.getEquipmentId()).isEqualTo(5L);
        assertThat(entry.getUserId()).isEqualTo(10L);
        assertThat(entry.getPriority()).isEqualTo(3); // 2 + 1
        assertThat(entry.getStatus()).isEqualTo("WAITING");
    }

    @Test
    void rejectsDuplicateActiveWaitlistForSameUserAndEquipment() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(microscope));
        when(waitlistRepository.existsByUserIdAndEquipmentIdAndStatus(10L, 5L, "WAITING")).thenReturn(true);

        WaitlistRequest req = new WaitlistRequest(
                5L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "Duplicate request"
        );

        assertThrows(IllegalArgumentException.class, () -> waitlistService.joinWaitlist(10L, req));
    }

    @Test
    void promotesTopWaitlistWhenSlotFreed() {
        WaitlistEntry top = new WaitlistEntry(
                5L, 10L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "Waiting"
        );
        when(waitlistRepository.findEligibleForPromotion(eq(5L), any(), any()))
                .thenReturn(List.of(top));
        when(waitlistRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<WaitlistEntry> promoted = waitlistService.promoteNextEligibleWaitlist(
                5L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0)
        );

        assertThat(promoted).isPresent();
        assertThat(promoted.get().getStatus()).isEqualTo("OFFERED");
        assertThat(promoted.get().getNotifiedAt()).isNotNull();
    }

    @Test
    void createsRecurringBookingAndTracksConflicts() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 5, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 5, 12, 0);

        // Occurrence 1: available; Occurrence 2: conflict; Occurrence 3: available
        when(bookingService.getAvailability(eq(5L), eq(start), eq(end)))
                .thenReturn(new BookingAvailability(5L, start, end, true, 1, 1));
        when(bookingService.getAvailability(eq(5L), eq(start.plusWeeks(1)), eq(end.plusWeeks(1))))
                .thenReturn(new BookingAvailability(5L, start.plusWeeks(1), end.plusWeeks(1), false, 0, 1));
        when(bookingService.getAvailability(eq(5L), eq(start.plusWeeks(2)), eq(end.plusWeeks(2))))
                .thenReturn(new BookingAvailability(5L, start.plusWeeks(2), end.plusWeeks(2), true, 1, 1));

        when(bookingService.createBooking(any(), any())).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(101L);
            return b;
        });

        RecurringBookingRequest req = new RecurringBookingRequest(
                5L, start, end, "WEEKLY", 3, "Weekly research lab"
        );
        req.setRequireAllOrNothing(false);

        RecurringBookingResponse res = recurringBookingService.createRecurringBooking(10L, req);

        assertThat(res.getSuccessCount()).isEqualTo(2);
        assertThat(res.getConflictCount()).isEqualTo(1);
        assertThat(res.getConflictedSlots()).containsExactly(start.plusWeeks(1));
    }

    @Test
    void atomicRecurringBookingFailsWhenAnyOccurrenceConflicts() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 5, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 5, 12, 0);

        when(bookingService.getAvailability(eq(5L), eq(start), eq(end)))
                .thenReturn(new BookingAvailability(5L, start, end, true, 1, 1));
        when(bookingService.getAvailability(eq(5L), eq(start.plusWeeks(1)), eq(end.plusWeeks(1))))
                .thenReturn(new BookingAvailability(5L, start.plusWeeks(1), end.plusWeeks(1), false, 0, 1));

        RecurringBookingRequest req = new RecurringBookingRequest(
                5L, start, end, "WEEKLY", 2, "Atomic request"
        );
        req.setRequireAllOrNothing(true);

        assertThrows(IllegalArgumentException.class, () -> recurringBookingService.createRecurringBooking(10L, req));
    }

    @Test
    void seriesApprovalAndRejectionUpdatesAllPendingOccurrences() {
        Booking b1 = new Booking();
        b1.setId(101L);
        b1.setUserId(10L);
        b1.setEquipmentId(5L);
        b1.setStatus("PENDING");
        b1.setPurpose("[SERIES:REC-1234] Project Experiment (1/2 - WEEKLY)");

        Booking b2 = new Booking();
        b2.setId(102L);
        b2.setUserId(10L);
        b2.setEquipmentId(5L);
        b2.setStatus("PENDING");
        b2.setPurpose("[SERIES:REC-1234] Project Experiment (2/2 - WEEKLY)");

        when(bookingRepository.findById(101L)).thenReturn(Optional.of(b1));
        when(bookingRepository.findByUserId(10L)).thenReturn(List.of(b1, b2));

        int approvedCount = recurringBookingService.approveRecurringSeries(101L, 999L, "manager@lab.edu");
        assertThat(approvedCount).isEqualTo(2);
        verify(bookingService).updateBookingStatus(101L, "CONFIRMED", "manager@lab.edu", "Series Approved");
        verify(bookingService).updateBookingStatus(102L, "CONFIRMED", "manager@lab.edu", "Series Approved");

        int rejectedCount = recurringBookingService.rejectRecurringSeries(101L, 999L, "manager@lab.edu", "Maintenance scheduled");
        assertThat(rejectedCount).isEqualTo(2);
        verify(bookingService).updateBookingStatus(101L, "REJECTED", "manager@lab.edu", "Maintenance scheduled");
        verify(bookingService).updateBookingStatus(102L, "REJECTED", "manager@lab.edu", "Maintenance scheduled");
    }

    @Test
    void claimWaitlistAcquiresPessimisticRowLockAndVerifiesOfferedStatus() {
        WaitlistEntry entry = new WaitlistEntry(
                5L, 10L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "Claimable waitlist"
        );
        entry.setId(50L);
        entry.setStatus("OFFERED");
        entry.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(waitlistRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(entry));
        when(bookingService.createBooking(any(), any())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(201L);
            return b;
        });

        Booking claimed = waitlistService.claimWaitlist(50L, 10L, "Claim purpose");
        assertThat(claimed).isNotNull();
        assertThat(claimed.getId()).isEqualTo(201L);
        assertThat(entry.getStatus()).isEqualTo("CONVERTED");

        // If already converted, subsequent claim attempt must be rejected
        assertThrows(IllegalStateException.class, () -> waitlistService.claimWaitlist(50L, 10L, "Duplicate claim"));
    }

    @Test
    void expireStaleOffersCleansUpAndPromotesNextCandidate() {
        WaitlistEntry stale = new WaitlistEntry(
                5L, 10L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "Expired waitlist"
        );
        stale.setId(60L);
        stale.setStatus("OFFERED");
        stale.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        when(waitlistRepository.findByStatusAndExpiresAtBefore(eq("OFFERED"), any()))
                .thenReturn(List.of(stale));

        int expiredCount = waitlistService.expireStaleOffers();
        assertThat(expiredCount).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void releasedTimeEventTriggersNextWaitlistPromotion() {
        WaitlistEntry next = new WaitlistEntry(
                5L, 20L,
                LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 12, 0),
                "Next in queue"
        );
        when(waitlistRepository.findEligibleForPromotion(eq(5L), any(), any()))
                .thenReturn(List.of(next));

        com.labresource.platform.booking.event.BookingReleasedEvent event =
                new com.labresource.platform.booking.event.BookingReleasedEvent(
                        5L,
                        LocalDateTime.of(2026, 11, 5, 10, 0),
                        LocalDateTime.of(2026, 11, 5, 12, 0)
                );

        waitlistService.onBookingReleased(event);

        assertThat(next.getStatus()).isEqualTo("OFFERED");
        assertThat(next.getExpiresAt()).isNotNull();
    }

    @Test
    void providesAlternativeTimeSlotsAndAlternativeEquipmentWhenBusy() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 5, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 5, 12, 0);

        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(microscope));
        // Requested slot is busy
        when(bookingService.getAvailability(eq(5L), eq(start), eq(end)))
                .thenReturn(new BookingAvailability(5L, start, end, false, 0, 1));

        // +2h slot is available
        when(bookingService.getAvailability(eq(5L), eq(start.plusHours(2)), eq(end.plusHours(2))))
                .thenReturn(new BookingAvailability(5L, start.plusHours(2), end.plusHours(2), true, 1, 1));

        Equipment alt = new Equipment();
        alt.setId(6L);
        alt.setName("Optical Microscope B");
        alt.setInstitutionId(1L);
        alt.setCategory("Microscopy");
        alt.setStatus("AVAILABLE");

        when(equipmentRepository.findAll()).thenReturn(List.of(microscope, alt));
        when(bookingService.getAvailability(eq(6L), eq(start), eq(end)))
                .thenReturn(new BookingAvailability(6L, start, end, true, 1, 1));

        SchedulingRecommendationDto rec = recurringBookingService.getRecommendations(5L, start, end);

        assertThat(rec.getIsAvailableAtRequestedTime()).isFalse();
        assertThat(rec.getAlternativeTimeSlots()).isNotEmpty();
        assertThat(rec.getAlternativeEquipment()).isNotEmpty();
        assertThat(rec.getAlternativeEquipment().getFirst().getEquipmentName()).isEqualTo("Optical Microscope B");
    }
    @Test
    void managerCancellationRequiresReasonAndOnlyFuturePendingConfirmedOccurrencesAreCancelled() {
        Booking base = new Booking();
        base.setId(301L);
        base.setUserId(10L);
        base.setEquipmentId(5L);
        base.setStatus("CONFIRMED");
        base.setStartTime(LocalDateTime.now().plusDays(1));
        base.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        base.setPurpose("Series [SERIES:XYZ123]");

        // 1. Past completed booking
        Booking past = new Booking();
        past.setId(302L);
        past.setUserId(10L);
        past.setEquipmentId(5L);
        past.setStatus("COMPLETED");
        past.setStartTime(LocalDateTime.now().minusDays(2));
        past.setEndTime(LocalDateTime.now().minusDays(2).plusHours(2));
        past.setPurpose("Series [SERIES:XYZ123]");

        // 2. Active started booking
        Booking active = new Booking();
        active.setId(303L);
        active.setUserId(10L);
        active.setEquipmentId(5L);
        active.setStatus("IN_USE");
        active.setStartTime(LocalDateTime.now().minusMinutes(30));
        active.setEndTime(LocalDateTime.now().plusMinutes(90));
        active.setPurpose("Series [SERIES:XYZ123]");

        // 3. Future pending booking
        Booking futurePending = new Booking();
        futurePending.setId(304L);
        futurePending.setUserId(10L);
        futurePending.setEquipmentId(5L);
        futurePending.setStatus("PENDING_APPROVAL");
        futurePending.setStartTime(LocalDateTime.now().plusDays(3));
        futurePending.setEndTime(LocalDateTime.now().plusDays(3).plusHours(2));
        futurePending.setPurpose("Series [SERIES:XYZ123]");

        // 4. Already cancelled booking
        Booking alreadyCancelled = new Booking();
        alreadyCancelled.setId(305L);
        alreadyCancelled.setUserId(10L);
        alreadyCancelled.setEquipmentId(5L);
        alreadyCancelled.setStatus("CANCELLED");
        alreadyCancelled.setStartTime(LocalDateTime.now().plusDays(5));
        alreadyCancelled.setEndTime(LocalDateTime.now().plusDays(5).plusHours(2));
        alreadyCancelled.setPurpose("Series [SERIES:XYZ123]");

        when(bookingRepository.findById(301L)).thenReturn(Optional.of(base));
        when(bookingRepository.findByUserId(10L)).thenReturn(List.of(base, past, active, futurePending, alreadyCancelled));

        // Test 1: Manager cancellation with null or empty reason must be rejected
        assertThrows(IllegalArgumentException.class, () ->
                recurringBookingService.cancelRecurringSeries(301L, 999L, true, null));
        assertThrows(IllegalArgumentException.class, () ->
                recurringBookingService.cancelRecurringSeries(301L, 999L, true, "   "));

        // Test 2: Manager cancellation with valid reason must only cancel future occurrences (base + futurePending)
        int cancelledCount = recurringBookingService.cancelRecurringSeries(301L, 999L, true, "Emergency maintenance required");
        assertThat(cancelledCount).isEqualTo(2); // Only 301 and 304

        verify(bookingService).cancelBooking(301L, "Manager [Reason: Emergency maintenance required]");
        verify(bookingService).cancelBooking(304L, "Manager [Reason: Emergency maintenance required]");
        verify(bookingService, org.mockito.Mockito.never()).cancelBooking(eq(302L), any());
        verify(bookingService, org.mockito.Mockito.never()).cancelBooking(eq(303L), any());
        verify(bookingService, org.mockito.Mockito.never()).cancelBooking(eq(305L), any());
    }
}