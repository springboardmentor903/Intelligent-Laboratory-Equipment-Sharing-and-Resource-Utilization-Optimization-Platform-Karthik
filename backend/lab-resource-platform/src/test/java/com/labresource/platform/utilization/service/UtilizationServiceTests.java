package com.labresource.platform.utilization.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilizationServiceTests {
    @Mock private UtilizationRepository utilizationRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;

    private UtilizationService service;

    @BeforeEach
    void setUp() {
        service = new UtilizationService(utilizationRepository,
                equipmentRepository, userRepository, bookingRepository);
    }

    @Test
    void rejectsUnknownEquipmentAndDoesNotSave() {
        Utilization record = validRecord();
        when(equipmentRepository.existsById(2L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.createRecord(record));

        verify(utilizationRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownUserAndDoesNotSave() {
        Utilization record = validRecord();
        when(equipmentRepository.existsById(2L)).thenReturn(true);
        when(userRepository.existsById(3L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.createRecord(record));

        verify(utilizationRepository, never()).save(any());
    }

    @Test
    void rejectsBookingThatDoesNotMatchEquipmentOrUser() {
        Utilization record = validRecord();
        record.setBookingId(4L);
        when(equipmentRepository.existsById(2L)).thenReturn(true);
        when(userRepository.existsById(3L)).thenReturn(true);
        Booking booking = new Booking();
        booking.setEquipmentId(2L);
        booking.setUserId(99L);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalArgumentException.class,
                () -> service.createRecord(record));

        verify(utilizationRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownBooking() {
        Utilization record = validRecord();
        record.setBookingId(4L);
        when(equipmentRepository.existsById(2L)).thenReturn(true);
        when(userRepository.existsById(3L)).thenReturn(true);
        when(bookingRepository.findById(4L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.createRecord(record));

        verify(utilizationRepository, never()).save(any());
    }

    private Utilization validRecord() {
        Utilization record = new Utilization();
        record.setEquipmentId(2L);
        record.setUserId(3L);
        record.setStartTime(LocalDateTime.now());
        return record;
    }
}
