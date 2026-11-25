package com.lifemate.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaturalLanguageExpenseRequestDTO {

    private Long accountId; // Defaults to 1 if omitted

    @NotBlank(message = "Raw text is required")
    private String rawText;

    private String currency; // Defaults to INR if omitted

    @Valid
    private ManualExpenseFallbackDTO manualFallback;
}