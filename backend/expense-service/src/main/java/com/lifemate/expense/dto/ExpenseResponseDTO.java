package com.lifemate.expense.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseResponseDTO {
    private Long id;
    private Long accountId;
    private String accountName;
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
    private String transactionType;
    private String merchant;
    private String rawInput;
    private LocalDateTime createdAt;
}