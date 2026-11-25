package com.lifemate.expense.dto;

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
public class CategoryBudgetResponseDTO {
    private Long id;
    private String name;
    private BigDecimal monthlyBudgetLimit;
}
