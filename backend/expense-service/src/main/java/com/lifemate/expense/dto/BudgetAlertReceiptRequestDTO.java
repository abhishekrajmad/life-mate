package com.lifemate.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BudgetAlertReceiptRequestDTO {
    @NotBlank
    private String eventId;
    @NotNull @Positive
    private Long categoryId;
    @NotNull @PositiveOrZero
    private BigDecimal monthlyLimit;
    @NotNull @PositiveOrZero
    private BigDecimal spent;
}
