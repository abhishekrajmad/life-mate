package com.lifemate.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseRequestDTO {

    @NotNull(message = "Account ID is required")
    @Positive(message = "Account ID must be greater than zero")
    private Long accountId;

    @NotBlank(message = "Category name is required")
    @Size(max = 50, message = "Category name must not exceed 50 characters")
    private String categoryName;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Amount must fit 10 integer and 2 decimal digits")
    private BigDecimal amount;

    @Pattern(regexp = "(?i)EXPENSE|INCOME", message = "Transaction type must be EXPENSE or INCOME")
    private String transactionType;

    @Size(max = 100, message = "Merchant must not exceed 100 characters")
    private String merchant;

    private String rawInput;
}
