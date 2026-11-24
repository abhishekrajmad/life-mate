package com.lifemate.expense.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryDTO {
    private BigDecimal totalBalance;
    private BigDecimal totalMonthlySpent;
    private BigDecimal totalMonthlyBudget;
    private List<CategoryBudgetStatus> categoryBudgets;
    private List<ExpenseResponseDTO> recentTransactions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryBudgetStatus {
        private Long categoryId;
        private String categoryName;
        private BigDecimal monthlyLimit;
        private BigDecimal spent;
        private double percentageUsed;
        private boolean exceeded;
    }
}