package com.lifemate.notification.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseCreatedEvent {
    private String eventId;
    private Long transactionId;
    private String userId;
    private Long accountId;
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
    private String transactionType;
    private LocalDateTime timestamp;
}
