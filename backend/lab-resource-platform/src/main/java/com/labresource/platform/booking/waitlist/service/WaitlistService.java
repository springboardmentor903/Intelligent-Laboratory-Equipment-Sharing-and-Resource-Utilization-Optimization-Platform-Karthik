package com.labresource.platform.booking.waitlist.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.event.BookingReleasedEvent;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.service.BookingService;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.booking.waitlist.dto.WaitlistRequest;
import com.labresource.platform.booking.waitlist.entity.WaitlistEntry;
import com.labresource.platform.booking.waitlist.repository.WaitlistRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final EquipmentRepository equipmentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final UserRepository userRepository;
    private final int holdWindowMinutes;

    @Autowired
    public WaitlistService(
            WaitlistRepository waitlistRepository,
            EquipmentRepository equipmentRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            UserRepository userRepository,
            @Value("${app.waitlist.hold-window-minutes:60}") int holdWindowMinutes) {
        this.userRepository = userRepository;
        this.waitlistRepository = waitlistRepository;
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.holdWindowMinutes = holdWindowMinutes > 0 ? holdWindowMinutes : 60;
    }

    public WaitlistService(
            WaitlistRepository waitlistRepository,
            EquipmentRepository equipmentRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            UserRepository userRepository) {
        this(waitlistRepository, equipmentRepository, bookingRepository, bookingService, userRepository, 60);
    }

    public WaitlistEntry joinWaitlist(Long userId, WaitlistRequest request) {
        if (request.getEquipmentId() == null || request.getRequestedStartTime() == null || request.getRequestedEndTime() == null) {
            throw new IllegalArgumentException("Equipment ID, start time, and end time are required to join waitlist.");
        }
        if (!request.getRequestedEndTime().isAfter(request.getRequestedStartTime())) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        Equipment equipment = equipmentRepository.findByIdForBooking(request.getEquipmentId())
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + request.getEquipmentId()));

        if (waitlistRepository.existsByUserIdAndEquipmentIdAndStatus(userId, equipment.getId(), "WAITING")) {
            throw new IllegalArgumentException("You are already on the active waitlist for this equipment.");
        }

        long queueLength = waitlistRepository.countByEquipmentIdAndStatus(equipment.getId(), "WAITING");

        WaitlistEntry entry = new WaitlistEntry(
                equipment.getId(),
                userId,
                request.getRequestedStartTime(),
                request.getRequestedEndTime(),
                request.getNotes()
        );
        entry.setPriority((int) queueLength + 1);

        return waitlistRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntry> getMyWaitlist(Long userId) {
        List<WaitlistEntry> entries = waitlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (WaitlistEntry entry : entries) {
            if ("WAITING".equals(entry.getStatus())) {
                long aheadCount = waitlistRepository.findByEquipmentIdAndStatusOrderByCreatedAtAsc(entry.getEquipmentId(), "WAITING")
                        .stream()
                        .filter(e -> e.getCreatedAt().isBefore(entry.getCreatedAt()) || (e.getCreatedAt().isEqual(entry.getCreatedAt()) && e.getId() != null && entry.getId() != null && e.getId() < entry.getId()))
                        .count();
                entry.setPriority((int) aheadCount + 1);
            }
        }
        return entries;
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntry> getEquipmentWaitlist(Long equipmentId) {
        return waitlistRepository.findByEquipmentIdAndStatusOrderByCreatedAtAsc(equipmentId, "WAITING");
    }

    public void cancelWaitlist(Long entryId, Long userId, boolean isStaff) {
        WaitlistEntry entry = waitlistRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Waitlist entry not found: " + entryId));
        if (!isStaff && !Objects.equals(entry.getUserId(), userId)) {
            throw new IllegalArgumentException("You can only cancel your own waitlist entries.");
        }
        entry.setStatus("CANCELLED");
        waitlistRepository.save(entry);
    }

    public Optional<WaitlistEntry> promoteNextEligibleWaitlist(Long equipmentId, LocalDateTime freedStart, LocalDateTime freedEnd) {
        List<WaitlistEntry> eligible = waitlistRepository.findEligibleForPromotion(equipmentId, freedStart, freedEnd);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        WaitlistEntry top = eligible.getFirst();
        LocalDateTime now = LocalDateTime.now();
        top.setStatus("OFFERED");
        top.setNotifiedAt(now);
        top.setExpiresAt(now.plusMinutes(holdWindowMinutes));
        log.info("Promoting waitlist entry {} for equipment {} (held until {})",
                top.getId(), equipmentId, top.getExpiresAt());
        return Optional.ofNullable(waitlistRepository.save(top));
    }

    /**
     * Listens for released time slots after committed booking cancellations or early check-outs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingReleased(BookingReleasedEvent event) {
        if (event != null && event.equipmentId() != null && event.startTime() != null && event.endTime() != null) {
            log.info("Booking released event received for equipment {}: {} - {}",
                    event.equipmentId(), event.startTime(), event.endTime());
            promoteNextEligibleWaitlist(event.equipmentId(), event.startTime(), event.endTime());
        }
    }

    public Booking claimWaitlist(Long entryId, Long userId, String purpose) {
        WaitlistEntry entry = waitlistRepository.findByIdForUpdate(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Waitlist entry not found: " + entryId));

        if (!Objects.equals(entry.getUserId(), userId)) {
            throw new IllegalArgumentException("You can only claim your own waitlist entry.");
        }
        if (!"OFFERED".equals(entry.getStatus()) && !"WAITING".equals(entry.getStatus())) {
            throw new IllegalStateException("This waitlist entry is no longer available (status: " + entry.getStatus() + ").");
        }

        LocalDateTime now = LocalDateTime.now();
        if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(now)) {
            entry.setStatus("EXPIRED");
            waitlistRepository.save(entry);
            promoteNextEligibleWaitlist(entry.getEquipmentId(), entry.getRequestedStartTime(), entry.getRequestedEndTime());
            throw new IllegalStateException("The hold window for this waitlist slot has expired.");
        }

        if (purpose == null || purpose.trim().isEmpty()) {
            throw new IllegalArgumentException("Purpose is required to confirm booking from waitlist.");
        }

        Booking booking = new Booking();
        booking.setEquipmentId(entry.getEquipmentId());
        booking.setUserId(userId);
        booking.setStartTime(entry.getRequestedStartTime());
        booking.setEndTime(entry.getRequestedEndTime());
        booking.setPurpose(purpose.trim());

        String email = userRepository.findById(userId).map(User::getEmail).orElse("waitlist@system.test");
        Booking created = bookingService.createBooking(booking, email);
        entry.setStatus("CONVERTED");
        waitlistRepository.save(entry);

        return created;
    }

    public void declineWaitlist(Long entryId, Long userId) {
        WaitlistEntry entry = waitlistRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Waitlist entry not found: " + entryId));
        if (!Objects.equals(entry.getUserId(), userId)) {
            throw new IllegalArgumentException("You can only decline your own waitlist offers.");
        }
        entry.setStatus("DECLINED");
        waitlistRepository.save(entry);
        promoteNextEligibleWaitlist(entry.getEquipmentId(), entry.getRequestedStartTime(), entry.getRequestedEndTime());
    }

    public Booking convertToBooking(Long entryId, Long userId) {
        WaitlistEntry entry = waitlistRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Waitlist entry not found: " + entryId));
        String purpose = (entry.getNotes() != null && !entry.getNotes().isBlank())
                ? "Waitlist Converted: " + entry.getNotes()
                : "Waitlist Converted";
        return claimWaitlist(entryId, userId, purpose);
    }

    @Scheduled(fixedDelay = 60000)
    public int expireStaleOffers() {
        LocalDateTime now = LocalDateTime.now();
        List<WaitlistEntry> expired = waitlistRepository.findByStatusAndExpiresAtBefore("OFFERED", now);
        int count = 0;
        for (WaitlistEntry entry : expired) {
            entry.setStatus("EXPIRED");
            waitlistRepository.save(entry);
            promoteNextEligibleWaitlist(entry.getEquipmentId(), entry.getRequestedStartTime(), entry.getRequestedEndTime());
            count++;
        }
        return count;
    }
}
