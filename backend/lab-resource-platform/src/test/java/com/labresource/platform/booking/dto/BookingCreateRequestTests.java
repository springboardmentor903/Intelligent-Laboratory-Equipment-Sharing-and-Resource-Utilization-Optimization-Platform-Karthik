package com.labresource.platform.booking.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingCreateRequestTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresPositiveEquipmentAndBothLocalDateTimes() {
        BookingCreateRequest request = new BookingCreateRequest();
        assertEquals(3, validator.validate(request).size());

        request.setEquipmentId(0L);
        request.setStartTime(java.time.LocalDateTime.of(2030, 1, 1, 9, 0));
        request.setEndTime(java.time.LocalDateTime.of(2030, 1, 1, 10, 0));
        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("equipmentId")));
    }

    @Test
    void limitsPurposeToTheExistingDatabaseColumnLength() {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setEquipmentId(1L);
        request.setStartTime(java.time.LocalDateTime.of(2030, 1, 1, 9, 0));
        request.setEndTime(java.time.LocalDateTime.of(2030, 1, 1, 10, 0));
        request.setPurpose("x".repeat(501));

        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("purpose")));
    }
}
