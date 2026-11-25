package com.lifemate.notification.listener;

import com.lifemate.notification.client.ExpenseServiceClient;
import com.lifemate.notification.dto.CategoryBudgetStatusDTO;
import com.lifemate.notification.dto.ExpenseCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BudgetAlertListenerTest {
    @Mock ExpenseServiceClient expenseServiceClient;
    @InjectMocks BudgetAlertListener listener;

    @Test
    void checksBudgetForExpenseInEventMonth(CapturedOutput output) {
        ExpenseCreatedEvent event = ExpenseCreatedEvent.builder().eventId("event-1")
                .userId("user-1")
                .transactionId(3L).categoryId(2L).transactionType("EXPENSE")
                .timestamp(LocalDateTime.of(2026, 10, 12, 10, 0)).build();
        CategoryBudgetStatusDTO status = new CategoryBudgetStatusDTO();
        status.setCategoryName("Food & Dining");
        status.setMonthlyLimit(new BigDecimal("100.00"));
        status.setSpent(new BigDecimal("110.00"));
        when(expenseServiceClient.getBudgetStatus("user-1", 2L, YearMonth.of(2026, 10), 3L))
                .thenReturn(status);
        when(expenseServiceClient.recordBudgetAlert("event-1", 2L,
                new BigDecimal("100.00"), new BigDecimal("110.00"))).thenReturn(true);

        assertDoesNotThrow(() -> listener.handleExpenseCreated(event));
        verify(expenseServiceClient).getBudgetStatus("user-1", 2L, YearMonth.of(2026, 10), 3L);
        assertTrue(output.getOut().contains("Budget exceeded for category Food & Dining"));
    }

    @Test
    void staysQuietWhenBudgetIsNotExceeded(CapturedOutput output) {
        ExpenseCreatedEvent event = ExpenseCreatedEvent.builder().eventId("event-2")
                .userId("user-1")
                .transactionId(4L).categoryId(2L).transactionType("EXPENSE")
                .timestamp(LocalDateTime.of(2026, 10, 13, 10, 0)).build();
        CategoryBudgetStatusDTO status = new CategoryBudgetStatusDTO();
        status.setMonthlyLimit(new BigDecimal("100.00"));
        status.setSpent(new BigDecimal("90.00"));
        when(expenseServiceClient.getBudgetStatus("user-1", 2L, YearMonth.of(2026, 10), 4L))
                .thenReturn(status);

        listener.handleExpenseCreated(event);
        assertFalse(output.getOut().contains("Budget exceeded"));
    }

    @Test
    void incomeDoesNotCheckExpenseBudget() {
        ExpenseCreatedEvent event = ExpenseCreatedEvent.builder().transactionType("INCOME").build();
        listener.handleExpenseCreated(event);
        verify(expenseServiceClient, never()).getBudgetStatus(any(), any(), any(), any());
    }

    @Test
    void replayedEventDoesNotEmitAnotherAlert(CapturedOutput output) {
        ExpenseCreatedEvent event = ExpenseCreatedEvent.builder().eventId("event-1")
                .userId("user-1")
                .transactionId(3L).categoryId(2L).transactionType("EXPENSE")
                .timestamp(LocalDateTime.of(2026, 10, 12, 10, 0)).build();
        CategoryBudgetStatusDTO status = new CategoryBudgetStatusDTO();
        status.setMonthlyLimit(new BigDecimal("100.00"));
        status.setSpent(new BigDecimal("110.00"));
        when(expenseServiceClient.getBudgetStatus("user-1", 2L, YearMonth.of(2026, 10), 3L))
                .thenReturn(status);
        when(expenseServiceClient.recordBudgetAlert("event-1", 2L,
                new BigDecimal("100.00"), new BigDecimal("110.00"))).thenReturn(false);

        listener.handleExpenseCreated(event);
        assertFalse(output.getOut().contains("Budget exceeded"));
    }
}
