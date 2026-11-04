package com.labresource.platform.equipment.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.api.EquipmentLiveStatus;
import com.labresource.platform.equipment.api.PageResponse;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EquipmentLiveStatusService {
    private static final List<String> CONFIRMED_STATUSES = List.of("APPROVED", "CONFIRMED");

    private final EquipmentRepository equipmentRepository;
    private final BookingRepository bookingRepository;
    private final UtilizationRepository utilizationRepository;

    public EquipmentLiveStatusService(
            EquipmentRepository equipmentRepository,
            BookingRepository bookingRepository,
            UtilizationRepository utilizationRepository) {
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.utilizationRepository = utilizationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<EquipmentLiveStatus> getPage(
            String query, String status, Long institutionId,
            int pageNumber, int pageSize, boolean includeManagerDetails) {
        LocalDateTime now = LocalDateTime.now();
        Page<Equipment> page = equipmentRepository.findForLiveMonitoring(
                institutionId, status, normalizeQuery(query),
                PageRequest.of(pageNumber, pageSize));
        List<EquipmentLiveStatus> content = buildStatuses(
                page.getContent(), now, includeManagerDetails);
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public EquipmentLiveStatus getOne(Long equipmentId, boolean includeManagerDetails) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found."));
        return buildStatuses(List.of(equipment), LocalDateTime.now(), includeManagerDetails)
                .getFirst();
    }

    private List<EquipmentLiveStatus> buildStatuses(
            List<Equipment> equipment, LocalDateTime now, boolean includeManagerDetails) {
        if (equipment.isEmpty()) return List.of();

        List<Long> equipmentIds = equipment.stream().map(Equipment::getId).toList();
        List<Utilization> activeUsage = utilizationRepository
                .findByEquipmentIdInAndEndTimeIsNull(equipmentIds);
        List<Long> bookingIds = activeUsage.stream().map(Utilization::getBookingId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Booking> activeBookings = new HashMap<>();
        bookingRepository.findAllById(bookingIds)
                .forEach(booking -> activeBookings.put(booking.getId(), booking));

        Map<Long, List<Utilization>> activeByEquipment = new HashMap<>();
        for (Utilization usage : activeUsage) {
            activeByEquipment.computeIfAbsent(usage.getEquipmentId(), ignored -> new ArrayList<>())
                    .add(usage);
        }
        activeByEquipment.values().forEach(records ->
                records.sort(Comparator.comparing(Utilization::getStartTime)));

        Map<Long, EquipmentLiveStatus.BookingWindow> nextByEquipment = new HashMap<>();
        bookingRepository.findByEquipmentIdInAndStatusInAndStartTimeAfterOrderByStartTimeAsc(
                        equipmentIds, CONFIRMED_STATUSES, now)
                .forEach(booking -> nextByEquipment.putIfAbsent(
                        booking.getEquipmentId(),
                        new EquipmentLiveStatus.BookingWindow(
                                booking.getId(), booking.getStartTime(), booking.getEndTime())));

        return equipment.stream().map(item -> {
            List<Utilization> sessions = activeByEquipment.getOrDefault(item.getId(), List.of());
            Utilization currentUsage = sessions.isEmpty() ? null : sessions.getFirst();
            Booking activeBooking = currentUsage == null ? null
                    : activeBookings.get(currentUsage.getBookingId());
            Long elapsedSeconds = currentUsage == null ? null
                    : Math.max(0, Duration.between(currentUsage.getStartTime(), now).toSeconds());
            EquipmentLiveStatus.ManagerUsageDetails details = includeManagerDetails
                    && currentUsage != null
                    ? new EquipmentLiveStatus.ManagerUsageDetails(
                            currentUsage.getId(), currentUsage.getUsageType(), currentUsage.getNotes())
                    : null;
            return new EquipmentLiveStatus(
                    item.getId(), item.getName(), item.getAssetTag(), item.getStatus(),
                    !sessions.isEmpty(),
                    currentUsage == null ? null : currentUsage.getStartTime(),
                    activeBooking == null ? null : activeBooking.getEndTime(),
                    elapsedSeconds, nextByEquipment.get(item.getId()), details);
        }).toList();
    }

    private String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }
}
