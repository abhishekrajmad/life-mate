package com.lifemate.expense.service;

import com.lifemate.expense.client.AiAgentClient;
import com.lifemate.expense.dto.AiParseResponseDTO;
import com.lifemate.expense.dto.ExpenseRequestDTO;
import com.lifemate.expense.dto.ManualExpenseFallbackDTO;
import com.lifemate.expense.dto.NaturalLanguageExpenseRequestDTO;
import com.lifemate.expense.dto.SmsWebhookRequestDTO;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.messaging.ExpenseOutboxWriter;
import com.lifemate.expense.exception.NonTransactionMessageException;
import com.lifemate.expense.repository.AccountRepository;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ExpenseService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class AccountBalanceConcurrencyIntegrationTest {

    @Autowired
    ExpenseService expenseService;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    PlatformTransactionManager transactionManager;

    @MockBean
    AiAgentClient aiAgentClient;
    @MockBean
    ExpenseOutboxWriter outboxWriter;

    private ExecutorService executor;
    private String userId;
    private Account account;
    private Category category;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        userId = "concurrency-" + UUID.randomUUID();
        account = accountRepository.saveAndFlush(Account.builder()
                .userId(userId)
                .accountName("Concurrency Test")
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .build());
        category = categoryRepository.saveAndFlush(Category.builder()
                .userId(userId)
                .name("Concurrency Test")
                .monthlyBudgetLimit(new BigDecimal("1000.00"))
                .build());
    }

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        transactionRepository.deleteAll(
                transactionRepository.findAllByAccount_UserIdOrderByCreatedAtDesc(userId));
        categoryRepository.findByIdAndUserId(category.getId(), userId)
                .ifPresent(categoryRepository::delete);
        accountRepository.findByIdAndUserId(account.getId(), userId)
                .ifPresent(accountRepository::delete);
    }

    @Test
    void concurrentExpensesWaitForTheAccountLockAndPreserveBothDeductions() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstExpenseCreated = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);

        Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
            expenseService.createExpense(userId, request("100.00", "First"));
            firstExpenseCreated.countDown();
            await(allowFirstCommit);
        }));

        assertTrue(firstExpenseCreated.await(5, TimeUnit.SECONDS));
        Future<?> second = executor.submit(
                () -> expenseService.createExpense(userId, request("200.00", "Second")));

        try {
            assertThrows(TimeoutException.class, () -> second.get(500, TimeUnit.MILLISECONDS));
            assertFalse(second.isDone(), "Second expense should wait for the first account lock");
        } finally {
            allowFirstCommit.countDown();
        }

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        Account reloaded = accountRepository.findByIdAndUserId(account.getId(), userId).orElseThrow();
        assertEquals(new BigDecimal("700.00"), reloaded.getBalance());
        assertEquals(2,
                transactionRepository.findAllByAccount_UserIdOrderByCreatedAtDesc(userId).size());
    }

    @Test
    void manualFallbackPersistsNaturalLanguageAndSmsTransactionsWithoutCallingAi() {
        var saved = expenseService.createFromNaturalLanguage(userId, NaturalLanguageExpenseRequestDTO.builder()
                .accountId(account.getId())
                .rawText("manual fallback integration")
                .manualFallback(ManualExpenseFallbackDTO.builder()
                        .amount(new BigDecimal("42.50"))
                        .categoryName(category.getName())
                        .merchant("Integration Store")
                        .build())
                .build());
        var smsSaved = expenseService.processSmsWebhook(userId, SmsWebhookRequestDTO.builder()
                .accountId(account.getId())
                .sender("Integration Bank")
                .body("manual fallback SMS integration")
                .manualFallback(ManualExpenseFallbackDTO.builder()
                        .amount(new BigDecimal("15.00"))
                        .categoryName(category.getName())
                        .merchant("SMS Store")
                        .build())
                .build());

        Account reloaded = accountRepository.findByIdAndUserId(account.getId(), userId).orElseThrow();
        assertEquals(new BigDecimal("42.50"), saved.getAmount());
        assertEquals("Integration Store", saved.getMerchant());
        assertEquals(new BigDecimal("15.00"), smsSaved.getAmount());
        assertEquals("SMS Store", smsSaved.getMerchant());
        assertEquals(new BigDecimal("942.50"), reloaded.getBalance());
        assertEquals(2,
                transactionRepository.findAllByAccount_UserIdOrderByCreatedAtDesc(userId).size());
        verify(aiAgentClient, never()).parseExpense(anyString(), anyString());
    }

    @Test
    void smsCreditIncreasesBalanceAndNonTransactionDoesNotChangeLedger() {
        when(aiAgentClient.parseExpense("Salary 50 credited", "INR"))
                .thenReturn(AiParseResponseDTO.builder()
                        .transactionType("INCOME")
                        .amount(new BigDecimal("50.00"))
                        .category(category.getName())
                        .merchant("Salary")
                        .build());

        var saved = expenseService.processSmsWebhook(userId, SmsWebhookRequestDTO.builder()
                .sender("BANK")
                .body("Salary 50 credited")
                .accountId(account.getId())
                .build());

        assertEquals("INCOME", saved.getTransactionType());
        assertEquals(new BigDecimal("1050.00"),
                accountRepository.findByIdAndUserId(account.getId(), userId).orElseThrow().getBalance());

        when(aiAgentClient.parseExpense("Your OTP is 123456", "INR"))
                .thenReturn(AiParseResponseDTO.builder().transactionType("NON_TRANSACTION").build());
        assertThrows(NonTransactionMessageException.class,
                () -> expenseService.processSmsWebhook(userId, SmsWebhookRequestDTO.builder()
                        .sender("BANK")
                        .body("Your OTP is 123456")
                        .accountId(account.getId())
                        .build()));

        assertEquals(new BigDecimal("1050.00"),
                accountRepository.findByIdAndUserId(account.getId(), userId).orElseThrow().getBalance());
        assertEquals(1, transactionRepository.findAllByAccount_UserIdOrderByCreatedAtDesc(userId).size());
    }

    private ExpenseRequestDTO request(String amount, String merchant) {
        return ExpenseRequestDTO.builder()
                .accountId(account.getId())
                .categoryName(category.getName())
                .amount(new BigDecimal(amount))
                .transactionType("EXPENSE")
                .merchant(merchant)
                .rawInput("concurrency integration test")
                .build();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release the first transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test was interrupted", exception);
        }
    }
}
