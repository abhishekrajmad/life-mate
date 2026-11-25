package com.lifemate.expense.service;

import com.lifemate.expense.client.AiAgentClient;
import com.lifemate.expense.dto.ExpenseCreatedEvent;
import com.lifemate.expense.dto.ExpenseRequestDTO;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.entity.Transaction;
import com.lifemate.expense.messaging.ExpenseOutboxWriter;
import com.lifemate.expense.repository.AccountRepository;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceEventTest {
    @Mock TransactionRepository transactionRepository;
    @Mock AccountRepository accountRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock AiAgentClient aiAgentClient;
    @Mock ExpenseOutboxWriter outboxWriter;
    @InjectMocks ExpenseService expenseService;

    @Test
    void publishesCreatedEventEvenWhenBudgetIsNotExceeded() {
        Account account = Account.builder().id(1L).userId("user-1")
                .accountName("Primary").balance(new BigDecimal("100.00")).build();
        Category category = Category.builder().id(2L).name("Food & Dining")
                .monthlyBudgetLimit(new BigDecimal("1000.00")).build();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 24, 12, 0);
        when(accountRepository.findByIdAndUserIdForUpdate(1L, "user-1"))
                .thenReturn(Optional.of(account));
        when(categoryRepository.findByUserIdAndNameIgnoreCase("user-1", "Food & Dining"))
                .thenReturn(Optional.of(category));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(3L);
            transaction.setCreatedAt(createdAt);
            return transaction;
        });

        expenseService.createExpense("user-1", ExpenseRequestDTO.builder().accountId(1L)
                .categoryName("Food & Dining").amount(new BigDecimal("10.00"))
                .transactionType("EXPENSE").build());

        ArgumentCaptor<ExpenseCreatedEvent> captor = ArgumentCaptor.forClass(ExpenseCreatedEvent.class);
        verify(outboxWriter).save(captor.capture());
        ExpenseCreatedEvent event = captor.getValue();
        assertNotNull(event.getEventId());
        assertEquals(3L, event.getTransactionId());
        assertEquals("user-1", event.getUserId());
        assertEquals(1L, event.getAccountId());
        assertEquals(2L, event.getCategoryId());
        assertEquals(new BigDecimal("10.00"), event.getAmount());
        assertEquals("EXPENSE", event.getTransactionType());
        assertEquals(createdAt, event.getTimestamp());
    }
}
