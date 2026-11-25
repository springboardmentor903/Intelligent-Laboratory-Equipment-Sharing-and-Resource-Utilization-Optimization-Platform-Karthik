package com.labresource.platform.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetOverviewItem {

    private Long departmentId;
    private String departmentName;
    private Integer fiscalYear;
    private Long budgetId;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private BigDecimal utilizationPercentage;
    private BudgetStatus status;
    private LocalDateTime updatedAt;
}
