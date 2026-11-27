package com.lifemate.expense.service;

import com.lifemate.expense.client.AiAgentClient;
import com.lifemate.expense.dto.AiParseResponseDTO;
import com.lifemate.expense.dto.ManualExpenseFallbackDTO;
import com.lifemate.expense.dto.NaturalLanguageExpenseRequestDTO;
import com.lifemate.expense.dto.SmsWebhookRequestDTO;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.entity.Transaction;
import com.lifemate.expense.exception.AiParsingUnavailableException;
import com.lifemate.expense.exception.NonTransactionMessageException;
import com.lifemate.expense.messaging.ExpenseOutboxWriter;
import com.lifemate.expense.repository.AccountRepository;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ExpenseServiceManualFallbackTest {
    @Mock
    TransactionRepository transactionRepository;
    @Mock
    AccountRepository accountRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    AiAgentClient aiAgentClient;
    @Mock
    ExpenseOutboxWriter outboxWriter;
    @InjectMocks
    ExpenseService expenseService;

    @Test
    void savesManuallyCategorizedExpenseWithoutCallingAi() {
        Account account = Account.builder().id(1L).userId("user-1")
                .accountName("Primary").balance(new BigDecimal("100.00")).build();
        Category category = Category.builder().id(2L).userId("user-1").name("Groceries")
                .monthlyBudgetLimit(new BigDecimal("5000.00")).build();
        when(accountRepository.findByIdAndUserIdForUpdate(1L, "user-1"))
                .thenReturn(Optional.of(account));
        when(categoryRepository.findByUserIdAndNameIgnoreCase("user-1", "Groceries"))
                .thenReturn(Optional.of(category));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(3L);
            transaction.setCreatedAt(LocalDateTime.of(2026, 11, 25, 12, 0));
            return transaction;
        });

        var saved = expenseService.createFromNaturalLanguage("user-1", NaturalLanguageExpenseRequestDTO.builder()
                .rawText("market purchase")
                .manualFallback(ManualExpenseFallbackDTO.builder()
                        .amount(new BigDecimal("24.50"))
                        .categoryName("Groceries")
                        .merchant("Local Market")
                        .build())
                .build());

        assertEquals(new BigDecimal("24.50"), saved.getAmount());
        assertEquals("Groceries", saved.getCategoryName());
        assertEquals("Local Market", saved.getMerchant());
        verify(aiAgentClient, never()).parseExpense(any(), any());
        verify(outboxWriter).save(any());
    }

    @Test
    void requiresManualFallbackWhenAiIsUnreachable() {
        when(aiAgentClient.parseExpense("purchase", "INR"))
                .thenThrow(new ResourceAccessException("AI service unavailable"));

        AiParsingUnavailableException exception = assertThrows(AiParsingUnavailableException.class,
                () -> expenseService.createFromNaturalLanguage("user-1", NaturalLanguageExpenseRequestDTO.builder()
                        .rawText("purchase")
                        .build()));

        assertEquals(true, exception.getMessage().contains("no transaction was saved"));
        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
    }

    @Test
    void requiresManualFallbackWhenAiResponseIsInvalid() {
        when(aiAgentClient.parseExpense("purchase", "INR"))
                .thenReturn(AiParseResponseDTO.builder()
                        .amount(new BigDecimal("-2.00"))
                        .category("Groceries")
                        .build());

        assertThrows(AiParsingUnavailableException.class,
                () -> expenseService.createFromNaturalLanguage("user-1", NaturalLanguageExpenseRequestDTO.builder()
                        .rawText("purchase")
                        .build()));
        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
    }

    @Test
    void rejectsUnsupportedAiCategoryWithoutSaving() {
        when(aiAgentClient.parseExpense("purchase", "INR"))
                .thenReturn(AiParseResponseDTO.builder()
                        .transactionType("EXPENSE")
                        .amount(new BigDecimal("24.50"))
                        .currency("INR")
                        .category("Travel")
                        .build());

        assertThrows(AiParsingUnavailableException.class,
                () -> expenseService.createFromNaturalLanguage("user-1",
                        NaturalLanguageExpenseRequestDTO.builder()
                                .rawText("purchase")
                                .build()));

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsUnsupportedAiCurrencyWithoutSaving() {
        when(aiAgentClient.parseExpense("purchase", "INR"))
                .thenReturn(AiParseResponseDTO.builder()
                        .transactionType("EXPENSE")
                        .amount(new BigDecimal("24.50"))
                        .currency("USD")
                        .category("Groceries")
                        .build());

        assertThrows(AiParsingUnavailableException.class,
                () -> expenseService.createFromNaturalLanguage("user-1",
                        NaturalLanguageExpenseRequestDTO.builder()
                                .rawText("purchase")
                                .build()));

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsUnsupportedManualFallbackCategoryWithoutSaving() {
        assertThrows(IllegalArgumentException.class,
                () -> expenseService.createFromNaturalLanguage("user-1",
                        NaturalLanguageExpenseRequestDTO.builder()
                                .rawText("market purchase")
                                .manualFallback(ManualExpenseFallbackDTO.builder()
                                        .amount(new BigDecimal("24.50"))
                                        .categoryName("Travel")
                                        .merchant("Local Market")
                                        .build())
                                .build()));

        verify(aiAgentClient, never()).parseExpense(any(), any());
        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsUsdAccountBeforeSavingTransaction() {
        Account account = Account.builder().id(1L).userId("user-1")
                .accountName("US Account").balance(new BigDecimal("100.00"))
                .currency("USD").build();
        when(accountRepository.findByIdAndUserIdForUpdate(1L, "user-1"))
                .thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> expenseService.createFromNaturalLanguage("user-1",
                        NaturalLanguageExpenseRequestDTO.builder()
                                .rawText("market purchase")
                                .manualFallback(ManualExpenseFallbackDTO.builder()
                                        .amount(new BigDecimal("24.50"))
                                        .categoryName("Groceries")
                                        .merchant("Local Market")
                                        .build())
                                .build()));

        verify(transactionRepository, never()).save(any());
        verify(outboxWriter, never()).save(any());
    }

    @Test
    void smsProcessingLogsDoNotContainSensitiveTransactionDetails(CapturedOutput output) {
        mockSmsAccount("1000.00");
        String sender = "PRIVATE_BANK_SENDER";
        String messageBody = "PRIVATE_SMS_BODY_938475";
        String merchant = "PRIVATE_MERCHANT";
        String amount = "123.45";
        when(aiAgentClient.parseExpense(messageBody, "INR"))
                .thenReturn(AiParseResponseDTO.builder()
                        .transactionType("EXPENSE")
                        .amount(new BigDecimal(amount))
                        .currency("INR")
                        .category("Other")
                        .merchant(merchant)
                        .build());

        expenseService.processSmsWebhook("user-1", SmsWebhookRequestDTO.builder()
                .sender(sender)
                .body(messageBody)
                .build());

        assertTrue(output.getOut().contains("Received SMS webhook"));
        assertFalse(output.getOut().contains(sender));
        assertFalse(output.getOut().contains(messageBody));
        assertFalse(output.getOut().contains(merchant));
        assertFalse(output.getOut().contains(amount));
    }

    @Test
    void recordsSmsDebitAsExpense() {
        Account account = mockSmsAccount("100.00");
        when(aiAgentClient.parseExpense("Rs 20 debited at Market", "INR"))
                .thenReturn(parsedSms("20.00", "EXPENSE"));

        var saved = expenseService.processSmsWebhook("user-1", sms("Rs 20 debited at Market"));

        assertEquals("EXPENSE", saved.getTransactionType());
        assertEquals(new BigDecimal("80.00"), account.getBalance());
    }

    @Test
    void recordsSmsCreditAsIncome() {
        Account account = mockSmsAccount("100.00");
        when(aiAgentClient.parseExpense("Salary 500 credited", "INR"))
                .thenReturn(AiParseResponseDTO.builder()
                        .transactionType("INCOME")
                        .amount(new BigDecimal("500.00"))
                        .category("Other")
                        .merchant("Salary")
                        .build());

        var saved = expenseService.processSmsWebhook("user-1", sms("Salary 500 credited"));

        assertEquals("INCOME", saved.getTransactionType());
        assertEquals(new BigDecimal("600.00"), account.getBalance());
    }

    @Test
    void doesNotSaveNonTransactionSms() {
        when(aiAgentClient.parseExpense("Your OTP is 123456", "INR"))
                .thenReturn(AiParseResponseDTO.builder()
                        .transactionType("NON_TRANSACTION")
                        .build());

        assertThrows(NonTransactionMessageException.class,
                () -> expenseService.processSmsWebhook("user-1", sms("Your OTP is 123456")));

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(transactionRepository, never()).save(any());
        verify(outboxWriter, never()).save(any());
    }

    private Account mockSmsAccount(String openingBalance) {
        Account account = Account.builder().id(1L).userId("user-1")
                .accountName("Primary").balance(new BigDecimal(openingBalance)).build();
        Category category = Category.builder().id(2L).userId("user-1").name("Other")
                .monthlyBudgetLimit(new BigDecimal("5000.00")).build();
        when(accountRepository.findByIdAndUserIdForUpdate(1L, "user-1"))
                .thenReturn(Optional.of(account));
        when(categoryRepository.findByUserIdAndNameIgnoreCase("user-1", "Other"))
                .thenReturn(Optional.of(category));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(3L);
            transaction.setCreatedAt(LocalDateTime.of(2026, 11, 25, 12, 0));
            return transaction;
        });
        return account;
    }

    private AiParseResponseDTO parsedSms(String amount, String transactionType) {
        return AiParseResponseDTO.builder()
                .transactionType(transactionType)
                .amount(new BigDecimal(amount))
                .category("Other")
                .merchant("Market")
                .build();
    }

    private SmsWebhookRequestDTO sms(String body) {
        return SmsWebhookRequestDTO.builder()
                .sender("BANK")
                .body(body)
                .build();
    }
}