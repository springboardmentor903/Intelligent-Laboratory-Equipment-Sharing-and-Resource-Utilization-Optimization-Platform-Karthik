package com.labresource.platform.booking.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.entity.BookingAuditLog;
import com.labresource.platform.booking.repository.BookingAuditLogRepository;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.labresource.platform.equipment.event.EquipmentStatusChangedEvent;
import com.labresource.platform.booking.event.BookingReleasedEvent;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import com.labresource.platform.booking.waitlist.repository.WaitlistRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class BookingService {
    private static final List<String> CAPACITY_HOLDING_STATUSES =
            List.of("PENDING", "APPROVED", "CONFIRMED", "IN_USE");

    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final BookingAuditLogRepository bookingAuditLogRepository;
    private final UtilizationRepository utilizationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private WaitlistRepository waitlistRepository;

    public void setWaitlistRepository(WaitlistRepository waitlistRepository) {
        this.waitlistRepository = waitlistRepository;
    }

    public BookingService(
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository) {
        this(bookingRepository, equipmentRepository, userRepository, null, null, null);
    }

    public BookingService(
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            BookingAuditLogRepository bookingAuditLogRepository) {
        this(bookingRepository, equipmentRepository, userRepository,
                bookingAuditLogRepository, null, null);
    }

    @Autowired
    public BookingService(
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            @Autowired(required = false) BookingAuditLogRepository bookingAuditLogRepository,
            @Autowired(required = false) UtilizationRepository utilizationRepository,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.bookingAuditLogRepository = bookingAuditLogRepository;
        this.utilizationRepository = utilizationRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Booking not found with id: " + id));
    }

    public boolean isUserOwner(Long userId, String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        User user = userRepository.findByEmail(email.trim())
                .orElse(null);

        return user != null && user.getId().equals(userId);
    }

    public List<Booking> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    public List<Booking> getBookingsByEquipment(Long equipmentId) {
        return bookingRepository.findByEquipmentId(equipmentId);
    }

    public List<Booking> getBookingsByStatus(String status) {
        return bookingRepository.findByStatus(status.toUpperCase());
    }

    @Transactional
    public Booking createBooking(
            Booking booking, String authenticatedEmail) {

        if (booking.getEquipmentId() == null
                || booking.getStartTime() == null
                || booking.getEndTime() == null) {
            throw new IllegalArgumentException(
                    "Equipment, start time and end time are required");
        }

        if (authenticatedEmail == null
                || authenticatedEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated user is required");
        }

        User authenticatedUser = userRepository
                .findByEmail(authenticatedEmail.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Authenticated user not found"));

        // Never trust the userId submitted by the frontend.
        booking.setUserId(authenticatedUser.getId());

        if (booking.getStartTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Booking start time must be in the future");
        }

        if (!booking.getEndTime().isAfter(booking.getStartTime())) {
            throw new IllegalArgumentException(
                    "Booking end time must be after start time");
        }

        // Lock the inventory row until this transaction commits so concurrent
        // requests for the same equipment cannot both consume the last unit.
        Equipment equipment = equipmentRepository
                .findByIdForBooking(booking.getEquipmentId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Equipment not found"));

        if (!isReservableStatus(equipment.getStatus())) {
            throw new IllegalArgumentException(
                    "Equipment is not available");
        }

        if (equipment.getQuantity() == null || equipment.getQuantity() < 1) {
            throw new IllegalArgumentException(
                    "Equipment quantity is invalid");
        }

        List<Booking> overlapping = bookingRepository
                .findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        booking.getEquipmentId(),
                        CAPACITY_HOLDING_STATUSES,
                        booking.getEndTime(),
                        booking.getStartTime());

        int peakConcurrent = calculatePeakConcurrency(
                overlapping,
                booking.getStartTime(),
                booking.getEndTime());

        if (peakConcurrent >= equipment.getQuantity()) {
            throw new IllegalArgumentException(
                    "No equipment units are available during the requested time");
        }

        booking.setId(null);
        booking.setStatus("PENDING");
        booking.setCreatedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        recordAudit(saved.getId(), "CREATED", null, saved.getStatus(), authenticatedEmail.trim());
        return saved;
    }

    public record BookingAvailability(
            Long equipmentId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean available,
            int remainingUnits,
            int capacity
    ) {}

    /** Availability is advisory; booking creation repeats the check under a row lock. */
    @Transactional(readOnly = true)
    public BookingAvailability getAvailability(
            Long equipmentId, LocalDateTime startTime, LocalDateTime endTime) {

        if (equipmentId == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Equipment id, start time and end time are required");
        }

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "End time must be after start time");
        }

        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Equipment not found"));

        int totalUnits = equipment.getQuantity() == null
                ? 0
                : equipment.getQuantity();

        if (totalUnits < 1 || !isReservableStatus(equipment.getStatus())) {
            return new BookingAvailability(
                    equipmentId, startTime, endTime, false, 0, totalUnits);
        }

        List<Booking> overlapping = bookingRepository
                .findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        equipmentId,
                        CAPACITY_HOLDING_STATUSES,
                        endTime,
                        startTime);

        int peakConcurrent = calculatePeakConcurrency(overlapping, startTime, endTime);
        if (waitlistRepository != null) {
            long heldOffers = waitlistRepository.countActiveOffersOverlapping(equipmentId, startTime, endTime, LocalDateTime.now());
            peakConcurrent += (int) heldOffers;
        }
        int remaining = Math.max(0, totalUnits - peakConcurrent);
        boolean available = remaining > 0;

        return new BookingAvailability(
                equipmentId, startTime, endTime, available, remaining, totalUnits);
    }

    private boolean isReservableStatus(String status) {
        return "AVAILABLE".equalsIgnoreCase(status)
                || "BOOKED".equalsIgnoreCase(status);
    }

    /**
     * Calculates the peak number of overlapping intervals during [windowStart, windowEnd)
     * using a sweep-line algorithm.
     * Intervals are half-open [start, end), clamped to the requested window.
     * At identical timestamps, END events are processed before START events.
     */
    private int calculatePeakConcurrency(
            List<Booking> overlappingBookings,
            LocalDateTime windowStart,
            LocalDateTime windowEnd) {

        if (overlappingBookings == null || overlappingBookings.isEmpty()) {
            return 0;
        }

        List<Event> events = new ArrayList<>(overlappingBookings.size() * 2);
        for (Booking booking : overlappingBookings) {
            if (booking == null || booking.getStartTime() == null || booking.getEndTime() == null) {
                continue;
            }
            LocalDateTime start = booking.getStartTime().isBefore(windowStart)
                    ? windowStart
                    : booking.getStartTime();
            LocalDateTime end = booking.getEndTime().isAfter(windowEnd)
                    ? windowEnd
                    : booking.getEndTime();

            if (end.isAfter(start)) {
                events.add(new Event(start, Event.EventType.START));
                events.add(new Event(end, Event.EventType.END));
            }
        }

        if (events.isEmpty()) {
            return 0;
        }

        Collections.sort(events);

        int current = 0;
        int peak = 0;
        for (Event event : events) {
            if (event.type() == Event.EventType.START) {
                current++;
                if (current > peak) {
                    peak = current;
                }
            } else {
                current--;
            }
        }
        return peak;
    }

    private record Event(LocalDateTime time, EventType type) implements Comparable<Event> {
        enum EventType {
            END,
            START
        }

        @Override
        public int compareTo(Event other) {
            int timeCmp = this.time.compareTo(other.time);
            if (timeCmp != 0) {
                return timeCmp;
            }
            return this.type.compareTo(other.type);
        }
    }

    public boolean isBookingOwner(Long bookingId, String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        Booking booking = getBookingById(bookingId);
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        return user.getId().equals(booking.getUserId());
    }

    public Booking updateBookingStatus(Long id, String status) {
        String actor = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                actor = auth.getName();
            }
        } catch (Exception ignored) {
        }
        return updateBookingStatus(id, status, actor);
    }

    @Transactional
    public Booking updateBookingStatus(Long id, String status, String actorEmail) {
        return updateBookingStatus(id, status, actorEmail, null);
    }

    @Transactional
    public Booking updateBookingStatus(Long id, String status, String actorEmail, String reason) {
        Booking booking = getBookingById(id);

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        String normalizedStatus = status.trim().toUpperCase();

        if (!List.of("APPROVED", "CONFIRMED", "REJECTED", "CANCELLED", "IN_USE", "COMPLETED", "NO_SHOW")
                .contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid booking status");
        }

        String current = booking.getStatus();
        boolean allowed = switch (current) {
            case "PENDING" -> List.of("APPROVED", "CONFIRMED", "REJECTED", "CANCELLED")
                    .contains(normalizedStatus);
            case "APPROVED", "CONFIRMED" -> List.of("CANCELLED", "NO_SHOW")
                    .contains(normalizedStatus);
            default -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException(
                    "Booking status transition from " + current + " to "
                            + normalizedStatus + " is not allowed");
        }

        booking.setStatus(normalizedStatus);
        Booking saved = bookingRepository.save(booking);
        String auditActor = (reason != null && !reason.isBlank()) ? actorEmail + " [Reason: " + reason.trim() + "]" : actorEmail;
        recordAudit(saved.getId(), "STATUS_CHANGED", current, normalizedStatus, auditActor);
        if (List.of("REJECTED", "CANCELLED").contains(normalizedStatus)) {
            publishBookingReleased(saved.getEquipmentId(), saved.getStartTime(), saved.getEndTime());
        }
        publishEquipmentChange(saved.getEquipmentId());

        return saved;
    }

    @Transactional
    public Booking checkIn(Long bookingId, String actorEmail) {
        return checkIn(bookingId, actorEmail, LocalDateTime.now());
    }

    @Transactional
    public Booking checkIn(Long bookingId, String actorEmail, LocalDateTime now) {
        requireUsageDependencies();
        Booking booking = lockBooking(bookingId);
        User actor = getActor(actorEmail);
        verifyOwner(booking, actor);
        if (!List.of("APPROVED", "CONFIRMED").contains(normalize(booking.getStatus()))) {
            throw new BookingActionException(HttpStatus.CONFLICT,
                    "Only confirmed bookings can be checked in.");
        }
        LocalDateTime checkInOpens = booking.getStartTime().minusMinutes(15);
        if (now.isBefore(checkInOpens) || now.isAfter(booking.getEndTime())) {
            throw new BookingActionException(HttpStatus.CONFLICT,
                    "Check-in is allowed from 15 minutes before the booking start until its scheduled end.");
        }
        if (utilizationRepository.existsByBookingId(bookingId)) {
            throw new BookingActionException(HttpStatus.CONFLICT,
                    "A usage session already exists for this booking.");
        }

        Equipment equipment = lockEquipment(booking.getEquipmentId());
        if (!List.of("AVAILABLE", "BOOKED").contains(normalize(equipment.getStatus()))) {
            throw new BookingActionException(HttpStatus.CONFLICT,
                    "This equipment is not available for check-in.");
        }
        int capacity = equipment.getQuantity() == null ? 1 : equipment.getQuantity();
        if (utilizationRepository.countByEquipmentIdAndEndTimeIsNull(booking.getEquipmentId()) >= capacity) {
            throw new BookingActionException(HttpStatus.CONFLICT,
                    "All units of this equipment are currently in use.");
        }

        Utilization usage = new Utilization();
        usage.setEquipmentId(booking.getEquipmentId());
        usage.setUserId(actor.getId());
        usage.setBookingId(bookingId);
        usage.setUsageType("BOOKING");
        usage.setStartTime(now);
        usage.setCreatedAt(now);
        utilizationRepository.save(usage);

        String previousStatus = booking.getStatus();
        booking.setStatus("IN_USE");
        equipment.setStatus("BOOKED");
        Booking saved = bookingRepository.save(booking);
        equipmentRepository.save(equipment);
        recordAudit(saved.getId(), "CHECKED_IN", previousStatus, "IN_USE", actor.getEmail());
        publishEquipmentChange(saved.getEquipmentId());
        return saved;
    }

    @Transactional
    public Booking checkOut(Long bookingId, String actorEmail) {
        return checkOut(bookingId, actorEmail, LocalDateTime.now());
    }

    @Transactional
    public Booking checkOut(Long bookingId, String actorEmail, LocalDateTime now) {
        requireUsageDependencies();
        Booking booking = lockBooking(bookingId);
        User actor = getActor(actorEmail);
        verifyOwner(booking, actor);
        if (!"IN_USE".equals(normalize(booking.getStatus()))) {
            throw new BookingActionException(HttpStatus.CONFLICT,
                    "Only an in-use booking can be checked out.");
        }
        Utilization usage = utilizationRepository.findOpenByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingActionException(HttpStatus.CONFLICT,
                        "No active usage session was found for this booking."));
        Equipment equipment = lockEquipment(booking.getEquipmentId());

        LocalDateTime actualEnd = now.isAfter(booking.getEndTime())
                ? booking.getEndTime() : now;
        closeUsage(usage, actualEnd);
        String previousStatus = booking.getStatus();
        booking.setStatus("COMPLETED");
        Booking saved = bookingRepository.save(booking);
        releaseEquipmentIfAvailable(equipment, saved.getEquipmentId(), now);
        recordAudit(saved.getId(), "CHECKED_OUT", previousStatus, "COMPLETED", actor.getEmail());
        if (actualEnd.isBefore(booking.getEndTime())) {
            publishBookingReleased(saved.getEquipmentId(), actualEnd, booking.getEndTime());
        }
        publishEquipmentChange(saved.getEquipmentId());
        return saved;
    }

    @Transactional
    public int markNoShows(LocalDateTime now) {
        requireUsageDependencies();
        int changed = 0;
        LocalDateTime cutoff = now.minusMinutes(15);
        for (Booking candidate : bookingRepository.findByStatusInAndStartTimeLessThanEqual(
                List.of("APPROVED", "CONFIRMED"), cutoff)) {
            Booking booking = lockBooking(candidate.getId());
            if (!List.of("APPROVED", "CONFIRMED").contains(normalize(booking.getStatus())
                    ) || booking.getStartTime().plusMinutes(15).isAfter(now)) continue;
            String previous = booking.getStatus();
            booking.setStatus("NO_SHOW");
            bookingRepository.save(booking);
            recordAudit(booking.getId(), "NO_SHOW", previous, "NO_SHOW", "SYSTEM");
            publishBookingReleased(booking.getEquipmentId(), booking.getStartTime(), booking.getEndTime());
            publishEquipmentChange(booking.getEquipmentId());
            changed++;
        }
        return changed;
    }

    @Transactional
    public int automaticallyCompleteExpiredBookings(LocalDateTime now) {
        requireUsageDependencies();
        int changed = 0;
        for (Booking candidate : bookingRepository.findByStatusInAndEndTimeLessThanEqual(
                List.of("IN_USE"), now)) {
            Booking booking = lockBooking(candidate.getId());
            if (!"IN_USE".equals(normalize(booking.getStatus()))
                    || booking.getEndTime().isAfter(now)) continue;
            Utilization usage = utilizationRepository.findOpenByBookingIdForUpdate(booking.getId())
                    .orElse(null);
            if (usage == null) continue;
            Equipment equipment = lockEquipment(booking.getEquipmentId());
            closeUsage(usage, booking.getEndTime());
            booking.setStatus("COMPLETED");
            Booking saved = bookingRepository.save(booking);
            releaseEquipmentIfAvailable(equipment, saved.getEquipmentId(), now);
            recordAudit(saved.getId(), "AUTO_COMPLETED", "IN_USE", "COMPLETED", "SYSTEM");
            publishEquipmentChange(saved.getEquipmentId());
            changed++;
        }
        return changed;
    }

    private Booking lockBooking(Long id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingActionException(HttpStatus.NOT_FOUND,
                        "Booking not found."));
    }

    private Equipment lockEquipment(Long id) {
        return equipmentRepository.findByIdForBooking(id)
                .orElseThrow(() -> new BookingActionException(HttpStatus.NOT_FOUND,
                        "Equipment not found."));
    }

    private User getActor(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            throw new BookingActionException(HttpStatus.FORBIDDEN,
                    "An authenticated booking owner is required.");
        }
        return userRepository.findByEmail(actorEmail.trim().toLowerCase())
                .orElseThrow(() -> new BookingActionException(HttpStatus.FORBIDDEN,
                        "The authenticated user is unavailable."));
    }

    private void verifyOwner(Booking booking, User actor) {
        if (!actor.getId().equals(booking.getUserId())) {
            throw new BookingActionException(HttpStatus.FORBIDDEN,
                    "Only the booking owner can perform this action.");
        }
    }

    private void closeUsage(Utilization usage, LocalDateTime endTime) {
        if (!endTime.isAfter(usage.getStartTime())) {
            throw new BookingActionException(HttpStatus.CONFLICT,
                    "The usage session end must be after its start.");
        }
        usage.setEndTime(endTime);
        usage.setActualHours(java.time.Duration.between(usage.getStartTime(), endTime)
                .toMillis() / 3_600_000.0);
        utilizationRepository.save(usage);
    }

    private void releaseEquipmentIfAvailable(Equipment equipment, Long equipmentId, LocalDateTime now) {
        if (!"BOOKED".equals(normalize(equipment.getStatus()))) return;
        List<Booking> activeOrUpcoming = bookingRepository
                .findByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        equipmentId, List.of("PENDING", "APPROVED", "CONFIRMED", "IN_USE"),
                        now.plusNanos(1), now);
        if (activeOrUpcoming.isEmpty()) {
            equipment.setStatus("AVAILABLE");
            equipmentRepository.save(equipment);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void requireUsageDependencies() {
        if (utilizationRepository == null) {
            throw new IllegalStateException("Usage tracking is not configured.");
        }
    }

    private void publishBookingReleased(Long equipmentId, LocalDateTime startTime, LocalDateTime endTime) {
        if (eventPublisher != null && equipmentId != null && startTime != null && endTime != null) {
            eventPublisher.publishEvent(new BookingReleasedEvent(equipmentId, startTime, endTime));
        }
    }

    private void publishEquipmentChange(Long equipmentId) {
        if (eventPublisher != null && equipmentId != null) {
            eventPublisher.publishEvent(new EquipmentStatusChangedEvent(equipmentId));
        }
    }

    @Transactional
    public void deleteBooking(Long id) {
        Booking booking = getBookingById(id);
        if (bookingAuditLogRepository != null) {
            bookingAuditLogRepository.deleteByBookingId(id);
        }
        bookingRepository.delete(booking);
    }

    public Booking cancelBooking(Long id) {
        String actor = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                actor = auth.getName();
            }
        } catch (Exception ignored) {
        }
        return cancelBooking(id, actor);
    }

    @Transactional
    public Booking cancelBooking(Long id, String actorEmail) {
        Booking booking = getBookingById(id);

        if (!List.of("PENDING", "APPROVED", "CONFIRMED")
                .contains(booking.getStatus())) {
            throw new IllegalArgumentException(
                    "Only pending or approved bookings can be cancelled");
        }

        String current = booking.getStatus();
        booking.setStatus("CANCELLED");
        Booking saved = bookingRepository.save(booking);
        recordAudit(saved.getId(), "CANCELLED", current, "CANCELLED", actorEmail);
        publishBookingReleased(saved.getEquipmentId(), saved.getStartTime(), saved.getEndTime());
        publishEquipmentChange(saved.getEquipmentId());

        return saved;
    }

    public List<BookingAuditLog> getBookingAuditHistory(Long bookingId) {
        if (bookingAuditLogRepository == null) {
            return List.of();
        }
        return bookingAuditLogRepository.findByBookingIdOrderByOccurredAtAsc(bookingId);
    }

    private void recordAudit(Long bookingId, String eventType, String previousStatus, String newStatus, String actorEmail) {
        if (bookingAuditLogRepository != null) {
            try {
                bookingAuditLogRepository.save(new BookingAuditLog(
                        bookingId, eventType, previousStatus, newStatus, actorEmail));
            } catch (Exception ignored) {
            }
        }
    }
}
