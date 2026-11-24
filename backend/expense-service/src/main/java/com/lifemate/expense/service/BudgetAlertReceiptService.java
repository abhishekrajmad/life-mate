package com.lifemate.expense.service;

import com.lifemate.expense.dto.BudgetAlertReceiptRequestDTO;
import com.lifemate.expense.dto.BudgetAlertReceiptResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BudgetAlertReceiptService {
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public BudgetAlertReceiptResponseDTO record(BudgetAlertReceiptRequestDTO request) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO budget_alert_receipts (event_id, category_id, monthly_limit, spent)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, request.getEventId(), request.getCategoryId(),
                request.getMonthlyLimit(), request.getSpent());
        return new BudgetAlertReceiptResponseDTO(inserted == 1);
    }
}
