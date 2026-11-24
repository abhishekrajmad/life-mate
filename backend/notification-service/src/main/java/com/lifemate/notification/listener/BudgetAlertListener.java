package com.lifemate.notification.listener;

import com.lifemate.notification.client.ExpenseServiceClient;
import com.lifemate.notification.dto.CategoryBudgetStatusDTO;
import com.lifemate.notification.dto.ExpenseCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;

@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetAlertListener {
    private final ExpenseServiceClient expenseServiceClient;

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void handleExpenseCreated(ExpenseCreatedEvent event) {
        if (!"EXPENSE".equalsIgnoreCase(event.getTransactionType())) {
            return;
        }
        CategoryBudgetStatusDTO status = expenseServiceClient.getBudgetStatus(
                event.getCategoryId(), YearMonth.from(event.getTimestamp()), event.getTransactionId());
        BigDecimal limit = status.getMonthlyLimit();
        BigDecimal spent = status.getSpent();
        if (limit != null && spent != null && limit.signum() > 0 && spent.compareTo(limit) > 0
                && expenseServiceClient.recordBudgetAlert(event.getEventId(), event.getCategoryId(), limit, spent)) {
            log.warn("Budget exceeded for category {}: spent {}, limit {}, over by {} (event {})",
                    status.getCategoryName(), spent, limit, spent.subtract(limit), event.getEventId());
        }
    }
}
