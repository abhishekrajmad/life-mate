package com.lifemate.expense.service;

import com.lifemate.expense.client.AiAgentClient;
import com.lifemate.expense.dto.ExpenseRequestDTO;
import com.lifemate.expense.dto.ExpenseResponseDTO;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.entity.Transaction;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.messaging.ExpenseOutboxWriter;
import com.lifemate.expense.repository.AccountRepository;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceLedgerMutationTest {

    private static final String USER_ID = "user-1";

    @Mock TransactionRepository transactionRepository;
    @Mock AccountRepository accountRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock AiAgentClient aiAgentClient;
    @Mock ExpenseOutboxWriter outboxWriter;
    @InjectMocks ExpenseService expenseService;

    @Test
    void updatingExpenseReversesOldAmountBeforeApplyingNewAmount() {
        Account account = account(1L, "900.00");
        Category category = category(1L, "Food & Dining");
        Transaction transaction = transaction(10L, account, category, "100.00", "EXPENSE");
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(10L, USER_ID))
                .thenReturn(Optional.of(transaction));
        when(accountRepository.findAllByUserIdAndIdInForUpdate(USER_ID, List.of(1L)))
                .thenReturn(List.of(account));
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Food & Dining"))
                .thenReturn(Optional.of(category));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        ExpenseResponseDTO response = expenseService.updateExpense(USER_ID, 10L,
                request(1L, "Food & Dining", "60.00", "expense"));

        assertEquals(new BigDecimal("940.00"), account.getBalance());
        assertEquals(new BigDecimal("60.00"), response.getAmount());
        assertEquals("EXPENSE", response.getTransactionType());
        verify(accountRepository).save(account);
        verify(outboxWriter, never()).save(any());
    }

    @Test
    void updateCanMoveTransactionAndChangeExpenseToIncome() {
        Account previousAccount = account(1L, "900.00");
        Account targetAccount = account(2L, "500.00");
        Category oldCategory = category(1L, "Food & Dining");
        Category newCategory = category(2L, "Other");
        Transaction transaction = transaction(10L, previousAccount, oldCategory, "100.00", "EXPENSE");
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(10L, USER_ID))
                .thenReturn(Optional.of(transaction));
        when(accountRepository.findAllByUserIdAndIdInForUpdate(USER_ID, List.of(1L, 2L)))
                .thenReturn(List.of(previousAccount, targetAccount));
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Other"))
                .thenReturn(Optional.of(newCategory));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        ExpenseResponseDTO response = expenseService.updateExpense(USER_ID, 10L,
                request(2L, "Other", "50.00", "INCOME"));

        assertEquals(new BigDecimal("1000.00"), previousAccount.getBalance());
        assertEquals(new BigDecimal("550.00"), targetAccount.getBalance());
        assertEquals(2L, response.getAccountId());
        assertEquals("Other", response.getCategoryName());
        assertEquals("INCOME", response.getTransactionType());
        verify(accountRepository).save(previousAccount);
        verify(accountRepository).save(targetAccount);
    }

    @Test
    void invalidUpdateDoesNotChangeBalance() {
        Account account = account(1L, "900.00");
        Category category = category(1L, "Food & Dining");
        Transaction transaction = transaction(10L, account, category, "100.00", "EXPENSE");
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(10L, USER_ID))
                .thenReturn(Optional.of(transaction));
        when(accountRepository.findAllByUserIdAndIdInForUpdate(USER_ID, List.of(1L)))
                .thenReturn(List.of(account));
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Food & Dining"))
                .thenReturn(Optional.of(category));

        assertThrows(IllegalArgumentException.class, () -> expenseService.updateExpense(
                USER_ID, 10L, request(1L, "Food & Dining", "60.00", "TRANSFER")));

        assertEquals(new BigDecimal("900.00"), account.getBalance());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deletingExpenseRestoresBalance() {
        Account account = account(1L, "900.00");
        Transaction transaction = transaction(
                10L, account, category(1L, "Food & Dining"), "100.00", "EXPENSE");
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(10L, USER_ID))
                .thenReturn(Optional.of(transaction));
        when(accountRepository.findByIdAndUserIdForUpdate(1L, USER_ID))
                .thenReturn(Optional.of(account));

        expenseService.deleteExpense(USER_ID, 10L);

        assertEquals(new BigDecimal("1000.00"), account.getBalance());
        verify(accountRepository).save(account);
        verify(transactionRepository).delete(transaction);
    }

    @Test
    void deletingIncomeRemovesItsBalanceIncrease() {
        Account account = account(1L, "1100.00");
        Transaction transaction = transaction(
                10L, account, category(2L, "Salary"), "100.00", "INCOME");
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(10L, USER_ID))
                .thenReturn(Optional.of(transaction));
        when(accountRepository.findByIdAndUserIdForUpdate(1L, USER_ID))
                .thenReturn(Optional.of(account));

        expenseService.deleteExpense(USER_ID, 10L);

        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }

    @Test
    void reportsMissingTransactionForReadUpdateAndDelete() {
        when(transactionRepository.findByIdAndAccount_UserId(99L, USER_ID)).thenReturn(Optional.empty());
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(99L, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getExpense(USER_ID, 99L));
        assertThrows(ResourceNotFoundException.class, () -> expenseService.updateExpense(
                USER_ID, 99L, request(1L, "Food & Dining", "10.00", "EXPENSE")));
        assertThrows(ResourceNotFoundException.class, () -> expenseService.deleteExpense(USER_ID, 99L));
    }

    @Test
    void doesNotReturnATransactionOwnedByAnotherUser() {
        when(transactionRepository.findByIdAndAccount_UserId(10L, "user-2"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getExpense("user-2", 10L));
        verify(transactionRepository).findByIdAndAccount_UserId(10L, "user-2");
    }

    private ExpenseRequestDTO request(
            Long accountId, String categoryName, String amount, String transactionType) {
        return ExpenseRequestDTO.builder()
                .accountId(accountId)
                .categoryName(categoryName)
                .amount(new BigDecimal(amount))
                .transactionType(transactionType)
                .merchant(" Merchant ")
                .rawInput("corrected input")
                .build();
    }

    private Account account(Long id, String balance) {
        return Account.builder()
                .id(id)
                .userId("user-1")
                .accountName("Account " + id)
                .balance(new BigDecimal(balance))
                .currency("INR")
                .build();
    }

    private Category category(Long id, String name) {
        return Category.builder().id(id).name(name)
                .monthlyBudgetLimit(new BigDecimal("5000.00"))
                .build();
    }

    private Transaction transaction(
            Long id, Account account, Category category, String amount, String transactionType) {
        return Transaction.builder()
                .id(id)
                .account(account)
                .category(category)
                .amount(new BigDecimal(amount))
                .transactionType(transactionType)
                .merchant("Original")
                .rawInput("original input")
                .createdAt(LocalDateTime.of(2026, 11, 25, 12, 0))
                .build();
    }
}
