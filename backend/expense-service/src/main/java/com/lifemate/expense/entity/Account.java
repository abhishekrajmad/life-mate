package com.lifemate.expense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(length = 10)
    private String currency;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.currency == null) {
            this.currency = "INR";
        }
    }
}