package com.labresource.platform.sharing.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.service.BookingActionException;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.entity.EquipmentOperatingHours;
import com.labresource.platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.entity.SharingListing;
import com.labresource.platform.sharing.repository.SharingAccessGrantRepository;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.sharing.repository.SharingListingRepository;
import com.labresource.platform.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ExternalBookingValidator {
    private static final List<String> EXTERNAL_STATUSES = List.of(
            "PENDING", "PENDING_APPROVAL", "APPROVED", "CONFIRMED", "IN_USE", "COMPLETED");

    private final SharingAgreementRepository agreements;
    private final SharingAccessGrantRepository grants;
    private final SharingListingRepository listings;
    private final EquipmentOperatingHoursRepository operatingHours;
    private final BookingRepository bookings;

    public ExternalBookingValidator(SharingAgreementRepository agreements,
                                    SharingAccessGrantRepository grants,
                                    SharingListingRepository listings,
                                    EquipmentOperatingHoursRepository operatingHours,
                                    BookingRepository bookings) {
        this.agreements = agreements;
        this.grants = grants;
        this.listings = listings;
        this.operatingHours = operatingHours;
        this.bookings = bookings;
    }

    public Long validate(User user, Equipment equipment, LocalDateTime start, LocalDateTime end) {
        if (user.getInstitutionId() == null || Objects.equals(user.getInstitutionId(), equipment.getInstitutionId())) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        SharingListing listing = listings.findByEquipmentId(equipment.getId())
                .filter(SharingListing::getActive)
                .orElseThrow(() -> new IllegalArgumentException("This equipment is not open for external booking."));

        SharingAgreement agreement = grants.findByUserId(user.getId()).stream()
                .filter(grant -> "ACTIVE".equals(grant.getStatus()))
                .map(grant -> agreements.findById(grant.getAgreementId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.getEquipmentId().equals(equipment.getId())
                        && "ACTIVE".equals(candidate.getStatus())
                        && candidate.getEndDate().isAfter(now)
                        && !start.isBefore(candidate.getStartDate())
                        && !end.isAfter(candidate.getEndDate()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "An active agreement and access grant covering the booking are required."));

        if (listing.getMinimumNoticeHours() != null && listing.getMinimumNoticeHours() > 0) {
            long noticeMinutes = Duration.between(now, start).toMinutes();
            if (noticeMinutes < (listing.getMinimumNoticeHours() * 60L - 1)) {
                throw new IllegalArgumentException("Booking does not meet the minimum notice period.");
            }
        }

        if (listing.getMaximumBookingHours() != null && listing.getMaximumBookingHours() > 0) {
            if (Duration.between(start, end).toMinutes() > listing.getMaximumBookingHours() * 60L) {
                throw new IllegalArgumentException("Booking exceeds the listing maximum duration.");
            }
        }

        if (agreement.getAllowedStartTime() != null || agreement.getAllowedEndTime() != null) {
            LocalTime allowedStart = agreement.getAllowedStartTime() != null ? agreement.getAllowedStartTime() : LocalTime.MIN;
            LocalTime allowedEnd = agreement.getAllowedEndTime() != null ? agreement.getAllowedEndTime() : LocalTime.MAX;

            if (start.toLocalDate().isBefore(end.toLocalDate())) {
                throw new IllegalArgumentException("Booking is outside the agreement allowed hours.");
            }
            if (start.toLocalTime().isBefore(allowedStart) || end.toLocalTime().isAfter(allowedEnd)) {
                throw new IllegalArgumentException("Booking is outside the agreement allowed hours.");
            }
        }

        enforceWeeklyLimit(equipment, listing, start, end);
        return agreement.getId();
    }

    private void enforceWeeklyLimit(Equipment equipment, SharingListing listing, LocalDateTime start, LocalDateTime end) {
        Map<Integer, EquipmentOperatingHours> hours = new HashMap<>();
        operatingHours.findByEquipmentIdOrderByDayOfWeekAsc(equipment.getId())
                .forEach(row -> hours.put(row.getDayOfWeek(), row));

        for (LocalDate monday = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
             monday.atStartOfDay().isBefore(end);
             monday = monday.plusWeeks(1)) {
            LocalDateTime weekStart = monday.atStartOfDay();
            LocalDateTime weekEnd = monday.plusDays(7).atStartOfDay();

            long capacity = 0;
            for (int day = 1; day <= 7; day++) {
                EquipmentOperatingHours row = hours.get(day);
                if (row != null && !Boolean.TRUE.equals(row.getIsClosed())) {
                    capacity += Duration.between(row.getOpenTime(), row.getCloseTime()).toMinutes();
                }
            }
            if (capacity == 0) {
                capacity = 7 * 8 * 60L;
            }
            capacity *= Math.max(1, equipment.getQuantity() == null ? 1 : equipment.getQuantity());

            long alreadyBooked = bookings.findOverlappingBookingsForEquipment(equipment.getId(), weekStart, weekEnd).stream()
                    .filter(booking -> booking.getSharingAgreementId() != null && EXTERNAL_STATUSES.contains(booking.getStatus()))
                    .mapToLong(booking -> operatingMinutes(booking.getStartTime(), booking.getEndTime(), weekStart, weekEnd, hours))
                    .sum();

            long requested = operatingMinutes(start, end, weekStart, weekEnd, hours);
            long allowed = BigDecimal.valueOf(capacity)
                    .multiply(listing.getExternalSharePercentage())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                    .longValue();

            if (alreadyBooked + requested > allowed) {
                throw new BookingActionException(HttpStatus.CONFLICT, "Booking exceeds the weekly external sharing limit.");
            }
        }
    }

    private long operatingMinutes(LocalDateTime start, LocalDateTime end, LocalDateTime weekStart,
                                  LocalDateTime weekEnd, Map<Integer, EquipmentOperatingHours> hours) {
        LocalDateTime from = start.isAfter(weekStart) ? start : weekStart;
        LocalDateTime until = end.isBefore(weekEnd) ? end : weekEnd;
        long total = 0;
        for (LocalDate day = from.toLocalDate(); !day.isAfter(until.toLocalDate()); day = day.plusDays(1)) {
            EquipmentOperatingHours row = hours.get(day.getDayOfWeek().getValue());
            LocalTime open = row == null ? LocalTime.of(9, 0) : row.getOpenTime();
            LocalTime close = row == null ? LocalTime.of(17, 0) : row.getCloseTime();
            if (row != null && Boolean.TRUE.equals(row.getIsClosed())) continue;
            LocalDateTime windowStart = day.atTime(open);
            LocalDateTime windowEnd = day.atTime(close);
            LocalDateTime overlapStart = from.isAfter(windowStart) ? from : windowStart;
            LocalDateTime overlapEnd = until.isBefore(windowEnd) ? until : windowEnd;
            if (overlapEnd.isAfter(overlapStart)) {
                total += Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }
        return total;
    }
}
