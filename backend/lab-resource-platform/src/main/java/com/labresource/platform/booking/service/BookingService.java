
package com.labresource.platform.booking.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    public BookingService(
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
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

        Equipment equipment = equipmentRepository
                .findById(booking.getEquipmentId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Equipment not found"));

        if (!"AVAILABLE".equalsIgnoreCase(equipment.getStatus())) {
            throw new IllegalArgumentException(
                    "Equipment is not available");
        }

        boolean hasConflict = bookingRepository
                .existsByEquipmentIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        booking.getEquipmentId(),
                        List.of("PENDING", "APPROVED"),
                        booking.getEndTime(),
                        booking.getStartTime());

        if (hasConflict) {
            throw new IllegalArgumentException(
                    "This equipment already has a booking during the requested time");
        }

        booking.setId(null);
        booking.setStatus("PENDING");
        booking.setCreatedAt(LocalDateTime.now());

        return bookingRepository.save(booking);
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
        Booking booking = getBookingById(id);

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        String normalizedStatus = status.trim().toUpperCase();

        if (!List.of("APPROVED", "REJECTED", "CANCELLED")
                .contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid booking status");
        }

        if (!"PENDING".equals(booking.getStatus())) {
            throw new IllegalArgumentException(
                    "Only pending bookings can change status");
        }

        booking.setStatus(normalizedStatus);

        return bookingRepository.save(booking);
    }

    public void deleteBooking(Long id) {
        Booking booking = getBookingById(id);
        bookingRepository.delete(booking);
    }

    public Booking cancelBooking(Long id) {
        Booking booking = getBookingById(id);

        if (!List.of("PENDING", "APPROVED")
                .contains(booking.getStatus())) {
            throw new IllegalArgumentException(
                    "Only pending or approved bookings can be cancelled");
        }

        booking.setStatus("CANCELLED");

        return bookingRepository.save(booking);
    }
}
