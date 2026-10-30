
package com.labresource.platform.utilization.service;

import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class UtilizationService {

    private final UtilizationRepository utilizationRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public UtilizationService(
            UtilizationRepository utilizationRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository) {
        this.utilizationRepository = utilizationRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<Utilization> getAllRecords() {
        return utilizationRepository.findAll();
    }

    public Utilization getRecordById(Long id) {
        return utilizationRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Utilization record not found with id: " + id
                        ));
    }

    public List<Utilization> getRecordsByEquipment(Long equipmentId) {
        return utilizationRepository.findByEquipmentId(equipmentId);
    }

    public List<Utilization> getRecordsByUser(Long userId) {
        return utilizationRepository.findByUserId(userId);
    }

    public List<Utilization> getRecordsByBooking(Long bookingId) {
        return utilizationRepository.findByBookingId(bookingId);
    }

    public Utilization createRecord(Utilization record) {
        if (record.getEquipmentId() == null
                || record.getUserId() == null
                || record.getStartTime() == null) {
            throw new IllegalArgumentException(
                    "Equipment, user and start time are required"
            );
        }

        if (!equipmentRepository.existsById(record.getEquipmentId())) {
            throw new IllegalArgumentException("Equipment not found");
        }
        if (!userRepository.existsById(record.getUserId())) {
            throw new IllegalArgumentException("User not found");
        }
        if (record.getBookingId() != null) {
            var booking = bookingRepository.findById(record.getBookingId())
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
            if (!Objects.equals(booking.getEquipmentId(), record.getEquipmentId())
                    || !Objects.equals(booking.getUserId(), record.getUserId())) {
                throw new IllegalArgumentException(
                        "Booking must belong to the specified equipment and user");
            }
        }

        if (record.getEndTime() != null
                && !record.getEndTime().isAfter(record.getStartTime())) {
            throw new IllegalArgumentException(
                    "End time must be after start time"
            );
        }

        if (record.getEndTime() != null) {
            double hours = Duration.between(
                    record.getStartTime(),
                    record.getEndTime()
            ).toMinutes() / 60.0;

            record.setActualHours(hours);
        } else {
            record.setActualHours(null);
        }

        record.setId(null);
        record.setCreatedAt(LocalDateTime.now());

        return utilizationRepository.save(record);
    }

    public void deleteRecord(Long id) {
        Utilization record = getRecordById(id);
        utilizationRepository.delete(record);
    }


    public Utilization updateRecord(Long id, Utilization updatedRecord) {
        Utilization existingRecord = getRecordById(id);

        if (updatedRecord.getEndTime() != null) {
            if (!updatedRecord.getEndTime().isAfter(existingRecord.getStartTime())) {
                throw new IllegalArgumentException(
                        "End time must be after start time"
                );
            }

            existingRecord.setEndTime(updatedRecord.getEndTime());

            double hours = java.time.Duration.between(
                    existingRecord.getStartTime(),
                    existingRecord.getEndTime()
            ).toMinutes() / 60.0;

            existingRecord.setActualHours(hours);
        }

        if (updatedRecord.getNotes() != null) {
            existingRecord.setNotes(updatedRecord.getNotes());
        }

        return utilizationRepository.save(existingRecord);
    }


    public long getTotalRecords() {
        return utilizationRepository.count();
    }

    public double getTotalUsageHours() {
        return utilizationRepository.findAll()
                .stream()
                .mapToDouble(record ->
                        record.getActualHours() == null
                                ? 0.0
                                : record.getActualHours())
                .sum();
    }


    public long getRecordCountByEquipment(Long equipmentId) {
        return utilizationRepository.countByEquipmentId(equipmentId);
    }


    public double getUsageHoursBetween(
            LocalDateTime start,
            LocalDateTime end) {

        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "A valid start and end time are required"
            );
        }

        return utilizationRepository.findByStartTimeBetween(start, end)
                .stream()
                .mapToDouble(record ->
                        record.getActualHours() == null
                                ? 0.0
                                : record.getActualHours())
                .sum();
    }

}
