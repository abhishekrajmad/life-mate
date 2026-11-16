package com.lifemate.expense.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetExceededEvent {
    private Long categoryId;
    private String categoryName;
    private BigDecimal monthlyLimit;
    private BigDecimal currentSpent;
    private BigDecimal exceededBy;
    private String triggeringMerchant;
    private BigDecimal triggeringAmount;
    private LocalDateTime occurredAt;
}