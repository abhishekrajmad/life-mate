package com.lifemate.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBudgetRequestDTO {

    @NotBlank(message = "Category name is required")
    @Size(max = 50, message = "Category name must not exceed 50 characters")
    private String name;

    @NotNull(message = "Monthly budget limit is required")
    @DecimalMin(value = "0.00", message = "Monthly budget limit must not be negative")
    @Digits(integer = 10, fraction = 2,
            message = "Monthly budget limit must fit 10 integer and 2 decimal digits")
    private BigDecimal monthlyBudgetLimit;
}
