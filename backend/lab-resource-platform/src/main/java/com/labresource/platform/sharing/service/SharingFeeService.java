package com.labresource.platform.sharing.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Reusable fee calculation service using BigDecimal (no floating-point math).
 * Formula: Fee = (Used or Booked Minutes * Fee per Hour) / 60
 * Calculations keep whole minutes and round ONLY at the end to 2 decimal places using RoundingMode.HALF_UP.
 */
@Service
public class SharingFeeService {

    public BigDecimal calculateFee(long minutes, BigDecimal feePerHour) {
        if (feePerHour == null || minutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(minutes)
                .multiply(feePerHour)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateEstimatedFee(LocalDateTime start, LocalDateTime end, BigDecimal feePerHour) {
        if (start == null || end == null || feePerHour == null || !end.isAfter(start)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long minutes = Duration.between(start, end).toMinutes();
        return calculateFee(minutes, feePerHour);
    }

    public BigDecimal calculateActualFee(long usedMinutes, BigDecimal feePerHour) {
        return calculateFee(usedMinutes, feePerHour);
    }
}
