package com.labresource.platform.sharing;

import com.labresource.platform.sharing.service.SharingFeeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SharingFeeServiceTests {

    private final SharingFeeService feeService = new SharingFeeService();

    @Test
    void calculatesStandardFeeCorrectlyWithHalfUpRounding() {
        // 90 minutes @ 100/hr = 150.00
        BigDecimal fee = feeService.calculateFee(90, new BigDecimal("100.00"));
        assertThat(fee).isEqualByComparingTo("150.00");

        // 70 minutes @ 100/hr = 7000 / 60 = 116.6666... -> 116.67
        BigDecimal roundedFee = feeService.calculateFee(70, new BigDecimal("100.00"));
        assertThat(roundedFee).isEqualByComparingTo("116.67");

        // 45 minutes @ 150/hr = 6750 / 60 = 112.50
        BigDecimal halfFee = feeService.calculateFee(45, new BigDecimal("150.00"));
        assertThat(halfFee).isEqualByComparingTo("112.50");
    }

    @Test
    void handlesZeroOrNullGracefully() {
        assertThat(feeService.calculateFee(0, new BigDecimal("100.00"))).isEqualByComparingTo("0.00");
        assertThat(feeService.calculateFee(60, null)).isEqualByComparingTo("0.00");
        assertThat(feeService.calculateFee(-10, new BigDecimal("100.00"))).isEqualByComparingTo("0.00");
    }

    @Test
    void calculatesEstimatedFeeFromTimestamps() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 25, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 25, 12, 30); // 150 min @ 200/hr = 500.00

        BigDecimal estimated = feeService.calculateEstimatedFee(start, end, new BigDecimal("200.00"));
        assertThat(estimated).isEqualByComparingTo("500.00");
    }
}
