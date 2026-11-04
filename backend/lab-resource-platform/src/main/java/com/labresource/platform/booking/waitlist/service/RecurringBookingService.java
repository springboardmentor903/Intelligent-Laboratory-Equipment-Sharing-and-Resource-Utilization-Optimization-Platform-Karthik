package com.labresource.platform.booking.waitlist.service;

import com.labresource.platform.analytics.dto.HeatmapDataDto;
import com.labresource.platform.analytics.service.UtilizationAnalyticsService;
import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.service.BookingService.BookingAvailability;
import com.labresource.platform.booking.service.BookingService;
import com.labresource.platform.booking.waitlist.dto.RecurringBookingRequest;
import com.labresource.platform.booking.waitlist.dto.RecurringBookingResponse;
import com.labresource.platform.booking.waitlist.dto.SchedulingRecommendationDto;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class RecurringBookingService {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private UtilizationAnalyticsService analyticsService;

    public RecurringBookingService(
            BookingService bookingService,
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository) {
        this.userRepository = userRepository;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.equipmentRepository = equipmentRepository;
    }

    public void setAnalyticsService(UtilizationAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public RecurringBookingResponse createRecurringBooking(Long userId, RecurringBookingRequest request) {
        if (request.getEquipmentId() == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Equipment ID, start time, and end time are required.");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        int occurrences = request.getOccurrences() != null && request.getOccurrences() >= 2
                ? Math.min(request.getOccurrences(), 24)
                : 4;

        String pattern = request.getRecurrencePattern() != null
                ? request.getRecurrencePattern().toUpperCase()
                : "WEEKLY";

        List<LocalDateTime[]> validIntervals = new ArrayList<>();
        List<LocalDateTime> conflicted = new ArrayList<>();
        Duration slotDuration = Duration.between(request.getStartTime(), request.getEndTime());

        for (int i = 0; i < occurrences; i++) {
            LocalDateTime occStart = calculateOccurrence(request.getStartTime(), pattern, i);
            LocalDateTime occEnd = occStart.plus(slotDuration);

            BookingAvailability availability = bookingService.getAvailability(
                    request.getEquipmentId(), occStart, occEnd);

            if (availability != null && availability.available()) {
                validIntervals.add(new LocalDateTime[]{occStart, occEnd});
            } else {
                conflicted.add(occStart);
            }
        }

        boolean requireAll = request.isRequireAllOrNothing();
        if (requireAll && !conflicted.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Cannot atomically create recurring booking: %d of %d occurrences conflict with existing reservations.",
                    conflicted.size(), occurrences));
        }

        String seriesId = "REC-" + UUID.randomUUID().toString().substring(0, 8);
        List<Booking> created = new ArrayList<>();
        String email = userRepository.findById(userId).map(User::getEmail).orElse("recurring@system.test");

        for (int i = 0; i < validIntervals.size(); i++) {
            LocalDateTime[] interval = validIntervals.get(i);
            Booking booking = new Booking();
            booking.setEquipmentId(request.getEquipmentId());
            booking.setUserId(userId);
            booking.setStartTime(interval[0]);
            booking.setEndTime(interval[1]);
            String userPurpose = request.getPurpose() != null && !request.getPurpose().isBlank()
                    ? request.getPurpose().trim()
                    : "Recurring Booking";
            booking.setPurpose(String.format("[SERIES:%s] %s (%d/%d - %s)", seriesId, userPurpose, i + 1, validIntervals.size(), pattern));

            Booking saved = bookingService.createBooking(booking, email);
            created.add(saved);
        }

        String summary = String.format("Successfully reserved %d of %d requested recurring occurrences (%d conflicted).",
                created.size(), occurrences, conflicted.size());

        return RecurringBookingResponse.builder()
                .createdBookings(created)
                .conflictedSlots(conflicted)
                .successCount(created.size())
                .conflictCount(conflicted.size())
                .summary(summary)
                .build();
    }

    @Transactional(readOnly = true)
    public RecurringBookingResponse previewRecurringBooking(Long userId, RecurringBookingRequest request) {
        if (request.getEquipmentId() == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Equipment ID, start time, and end time are required.");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        int occurrences = request.getOccurrences() != null && request.getOccurrences() >= 2
                ? Math.min(request.getOccurrences(), 24)
                : 4;

        String pattern = request.getRecurrencePattern() != null
                ? request.getRecurrencePattern().toUpperCase()
                : "WEEKLY";

        List<Booking> previewBookings = new ArrayList<>();
        List<LocalDateTime> conflicted = new ArrayList<>();
        Duration slotDuration = Duration.between(request.getStartTime(), request.getEndTime());

        for (int i = 0; i < occurrences; i++) {
            LocalDateTime occStart = calculateOccurrence(request.getStartTime(), pattern, i);
            LocalDateTime occEnd = occStart.plus(slotDuration);

            BookingAvailability availability = bookingService.getAvailability(
                    request.getEquipmentId(), occStart, occEnd);

            if (availability != null && availability.available()) {
                Booking booking = new Booking();
                booking.setEquipmentId(request.getEquipmentId());
                booking.setUserId(userId);
                booking.setStartTime(occStart);
                booking.setEndTime(occEnd);
                booking.setStatus("PENDING");
                booking.setPurpose(request.getPurpose() != null
                        ? String.format("%s (Recurring %d/%d - %s)", request.getPurpose(), i + 1, occurrences, pattern)
                        : String.format("Recurring Booking %d/%d", i + 1, occurrences));
                previewBookings.add(booking);
            } else {
                conflicted.add(occStart);
            }
        }

        String summary = String.format("Preview: %d of %d recurring occurrences available (%d conflicts).",
                previewBookings.size(), occurrences, conflicted.size());

        return RecurringBookingResponse.builder()
                .createdBookings(previewBookings)
                .conflictedSlots(conflicted)
                .successCount(previewBookings.size())
                .conflictCount(conflicted.size())
                .summary(summary)
                .build();
    }

    public int approveRecurringSeries(Long baseBookingId, Long actorUserId, String actorEmail) {
        Booking base = bookingRepository.findById(baseBookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + baseBookingId));

        List<Booking> series = findSeriesBookings(base);
        int count = 0;
        for (Booking b : series) {
            if ("PENDING".equalsIgnoreCase(b.getStatus()) || "PENDING_APPROVAL".equalsIgnoreCase(b.getStatus())) {
                bookingService.updateBookingStatus(b.getId(), "CONFIRMED", actorEmail, "Series Approved");
                count++;
            }
        }
        return count;
    }

    public int rejectRecurringSeries(Long baseBookingId, Long actorUserId, String actorEmail, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required.");
        }
        Booking base = bookingRepository.findById(baseBookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + baseBookingId));

        List<Booking> series = findSeriesBookings(base);
        int count = 0;
        for (Booking b : series) {
            if ("PENDING".equalsIgnoreCase(b.getStatus()) || "PENDING_APPROVAL".equalsIgnoreCase(b.getStatus())) {
                bookingService.updateBookingStatus(b.getId(), "REJECTED", actorEmail, reason.trim());
                count++;
            }
        }
        return count;
    }

    public int cancelRecurringSeries(Long baseBookingId, Long userId, boolean isStaff, String reason) {
        Booking base = bookingRepository.findById(baseBookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + baseBookingId));

        if (!isStaff && !Objects.equals(base.getUserId(), userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized to cancel this booking series");
        }

        if (isStaff && (reason == null || reason.trim().isEmpty())) {
            throw new IllegalArgumentException("Manager cancellation requires a mandatory reason.");
        }

        List<Booking> series = findSeriesBookings(base);
        int cancelledCount = 0;
        String actor = (isStaff ? "Manager" : "User") + (reason != null && !reason.trim().isEmpty() ? " [Reason: " + reason.trim() + "]" : "");
        LocalDateTime now = LocalDateTime.now();

        for (Booking b : series) {
            // Strictly only future occurrences
            if (b.getStartTime() == null || !b.getStartTime().isAfter(now)) {
                continue;
            }
            // Strictly only PENDING, PENDING_APPROVAL, CONFIRMED, or APPROVED
            String status = b.getStatus() != null ? b.getStatus().toUpperCase() : "";
            if ("PENDING".equals(status) || "PENDING_APPROVAL".equals(status) || "CONFIRMED".equals(status) || "APPROVED".equals(status)) {
                try {
                    bookingService.cancelBooking(b.getId(), actor);
                    cancelledCount++;
                } catch (Exception ignored) {
                }
            }
        }
        return cancelledCount;
    }

    private List<Booking> findSeriesBookings(Booking base) {
        String seriesId = extractSeriesId(base.getPurpose());
        if (seriesId != null) {
            return bookingRepository.findByUserId(base.getUserId()).stream()
                    .filter(b -> Objects.equals(b.getEquipmentId(), base.getEquipmentId()))
                    .filter(b -> b.getPurpose() != null && b.getPurpose().contains("[SERIES:" + seriesId + "]"))
                    .toList();
        }
        return bookingRepository.findByUserId(base.getUserId()).stream()
                .filter(b -> Objects.equals(b.getEquipmentId(), base.getEquipmentId()))
                .filter(b -> b.getPurpose() != null && b.getPurpose().contains("Recurring"))
                .toList();
    }

    private String extractSeriesId(String purpose) {
        if (purpose == null) return null;
        int start = purpose.indexOf("[SERIES:");
        if (start != -1) {
            int end = purpose.indexOf("]", start);
            if (end != -1) {
                return purpose.substring(start + 8, end);
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public SchedulingRecommendationDto getRecommendations(
            Long equipmentId, LocalDateTime requestedStart, LocalDateTime requestedEnd) {

        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

        BookingAvailability check = bookingService.getAvailability(equipmentId, requestedStart, requestedEnd);
        boolean isAvailable = check.available();

        Duration duration = Duration.between(requestedStart, requestedEnd);
        List<SchedulingRecommendationDto.TimeSlotRecommendation> alternativeSlots = new ArrayList<>();

        if (!isAvailable) {
            // Rule 1: Check adjacent slots within ±4h or +1-2 days
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime[] trialStarts = {
                    requestedStart.minusHours(4),
                    requestedStart.minusHours(2),
                    requestedStart.plusHours(2),
                    requestedStart.plusHours(4),
                    requestedStart.plusDays(1),
                    requestedStart.plusDays(2)
            };

            for (LocalDateTime tStart : trialStarts) {
                if (tStart.isBefore(now)) continue;
                LocalDateTime tEnd = tStart.plus(duration);
                BookingAvailability trialCheck = bookingService.getAvailability(equipmentId, tStart, tEnd);
                if (trialCheck != null && trialCheck.available()) {
                    String reason = tStart.toLocalDate().equals(requestedStart.toLocalDate())
                            ? (tStart.isBefore(requestedStart) ? "Earlier slot on same day with 0 conflicts" : "Later slot on same day with 0 conflicts")
                            : "Available slot on " + tStart.toLocalDate();
                    alternativeSlots.add(new SchedulingRecommendationDto.TimeSlotRecommendation(tStart, tEnd, reason));
                    if (alternativeSlots.size() >= 3) break;
                }
            }
        }

        // Rule 2: Alternative equipment in same institution and category
        List<SchedulingRecommendationDto.AlternativeEquipmentRecommendation> alternativeEquipment = new ArrayList<>();
        List<Equipment> sameInstitution = equipmentRepository.findAll().stream()
                .filter(eq -> Objects.equals(eq.getInstitutionId(), equipment.getInstitutionId()))
                .filter(eq -> !Objects.equals(eq.getId(), equipmentId))
                .filter(eq -> equipment.getCategory() == null || Objects.equals(eq.getCategory(), equipment.getCategory()))
                .toList();

        for (Equipment alt : sameInstitution) {
            BookingAvailability altCheck = bookingService.getAvailability(alt.getId(), requestedStart, requestedEnd);
            if (altCheck != null && altCheck.available() && "AVAILABLE".equalsIgnoreCase(alt.getStatus())) {
                alternativeEquipment.add(SchedulingRecommendationDto.AlternativeEquipmentRecommendation.builder()
                        .equipmentId(alt.getId())
                        .equipmentName(alt.getName())
                        .assetTag(alt.getAssetTag())
                        .category(alt.getCategory())
                        .quantity(alt.getQuantity())
                        .currentUtilizationRate(0.0)
                        .recommendationReason("Available in same category (" + alt.getCategory() + ") during requested window")
                        .build());
                if (alternativeEquipment.size() >= 3) break;
            }
        }

        // Rule 3: Quiet / Off-peak slots ranked by historical heatmap utilization
        List<SchedulingRecommendationDto.TimeSlotRecommendation> quietSlots = new ArrayList<>();
        if (analyticsService != null) {
            try {
                HeatmapDataDto heatmap = analyticsService.getHeatmapData(
                        equipment.getInstitutionId(), equipment.getDepartmentId(), equipmentId,
                        requestedStart.minusDays(28), requestedStart);

                Map<String, Double> scoreMap = new HashMap<>();
                if (heatmap != null && heatmap.getMatrix() != null) {
                    for (com.labresource.platform.analytics.dto.HeatmapCellDto cell : heatmap.getMatrix()) {
                        if (cell.getUtilizationScore() != null) {
                            scoreMap.put(cell.getDayOfWeek() + ":" + cell.getHour(), cell.getUtilizationScore());
                        }
                    }
                }

                LocalDate startDate = requestedStart.toLocalDate();
                record CandidateSlot(LocalDateTime start, LocalDateTime end, double avgScore) {}
                List<CandidateSlot> candidates = new ArrayList<>();

                for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                    LocalDate day = startDate.plusDays(dayOffset);
                    if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) continue;

                    int maxH = 17 - (int) Math.ceil(duration.toMinutes() / 60.0);
                    for (int h = 9; h <= maxH; h++) {
                        LocalDateTime cStart = day.atTime(h, 0);
                        LocalDateTime cEnd = cStart.plus(duration);

                        if (cStart.isBefore(requestedStart) || cStart.isEqual(requestedStart)) continue;

                        BookingAvailability avail = bookingService.getAvailability(equipmentId, cStart, cEnd);
                        if (avail != null && avail.available()) {
                            double score = scoreMap.getOrDefault(day.getDayOfWeek().getValue() + ":" + h, 0.0);
                            candidates.add(new CandidateSlot(cStart, cEnd, score));
                        }
                    }
                }

                candidates.sort(Comparator.comparing(CandidateSlot::avgScore)
                        .thenComparing(CandidateSlot::start));

                for (CandidateSlot c : candidates.stream().limit(3).toList()) {
                    quietSlots.add(new SchedulingRecommendationDto.TimeSlotRecommendation(
                            c.start(),
                            c.end(),
                            String.format("Off-peak historical slot (avg utilization %.1f%%)", c.avgScore())
                    ));
                }
            } catch (Exception ignored) {
            }
        }

        return SchedulingRecommendationDto.builder()
                .requestedEquipmentId(equipmentId)
                .requestedEquipmentName(equipment.getName())
                .isAvailableAtRequestedTime(isAvailable)
                .alternativeTimeSlots(alternativeSlots)
                .alternativeEquipment(alternativeEquipment)
                .quietTimeSlots(quietSlots)
                .build();
    }

    private LocalDateTime calculateOccurrence(LocalDateTime base, String pattern, int index) {
        return switch (pattern) {
            case "DAILY" -> base.plusDays(index);
            case "BIWEEKLY" -> base.plusWeeks(2L * index);
            case "MONTHLY" -> base.plusMonths(index);
            default -> base.plusWeeks(index); // WEEKLY
        };
    }
}
