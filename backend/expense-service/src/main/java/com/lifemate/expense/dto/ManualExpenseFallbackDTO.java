package com.lifemate.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualExpenseFallbackDTO {

    @NotNull(message = "Manual amount is required")
    @Positive(message = "Manual amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Manual amount must fit 10 integer and 2 decimal digits")
    private BigDecimal amount;

    @NotBlank(message = "Manual category is required")
    @Size(max = 50, message = "Manual category must not exceed 50 characters")
    private String categoryName;

    @Size(max = 100, message = "Manual merchant must not exceed 100 characters")
    private String merchant;

    @Pattern(regexp = "(?i)EXPENSE|INCOME", message = "Transaction type must be EXPENSE or INCOME")
    private String transactionType;
}