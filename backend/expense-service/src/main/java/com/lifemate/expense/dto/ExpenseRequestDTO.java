package com.lifemate.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseRequestDTO {

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotBlank(message = "Category name is required")
    private String categoryName;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String transactionType; // Defaults to EXPENSE if null

    private String merchant;

    private String rawInput;
}