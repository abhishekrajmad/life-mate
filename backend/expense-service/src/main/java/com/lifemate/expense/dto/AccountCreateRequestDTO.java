package com.lifemate.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
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
public class AccountCreateRequestDTO {

    @Null(message = "User ID is supplied by the authenticated identity")
    private String userId;

    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name must not exceed 100 characters")
    private String accountName;

    @NotNull(message = "Opening balance is required")
    @Digits(integer = 10, fraction = 2, message = "Opening balance must fit 10 integer and 2 decimal digits")
    private BigDecimal openingBalance;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "[A-Za-z]{3}", message = "Currency must be a 3-letter ISO 4217 code")
    private String currency;
}
