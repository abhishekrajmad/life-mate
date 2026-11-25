package com.lifemate.expense.service;

import com.lifemate.expense.dto.AccountCreateRequestDTO;
import com.lifemate.expense.dto.AccountResponseDTO;
import com.lifemate.expense.dto.AccountUpdateRequestDTO;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.exception.ResourceConflictException;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.repository.AccountRepository;
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
class AccountServiceTest {

    private static final String USER_ID = "user-1";

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @InjectMocks AccountService accountService;

    @Test
    void createsAccountWithTrimmedValuesAndNormalizedCurrency() {
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(10L);
            account.setCreatedAt(LocalDateTime.of(2026, 11, 25, 12, 0));
            return account;
        });

        AccountResponseDTO response = accountService.create(USER_ID, AccountCreateRequestDTO.builder()
                .accountName(" Travel Fund ")
                .openingBalance(new BigDecimal("250.00"))
                .currency("inr")
                .build());

        assertEquals(10L, response.getId());
        assertEquals(USER_ID, response.getUserId());
        assertEquals("Travel Fund", response.getAccountName());
        assertEquals(new BigDecimal("250.00"), response.getBalance());
        assertEquals("INR", response.getCurrency());
    }

    @Test
    void rejectsUnsupportedCurrency() {
        AccountCreateRequestDTO request = AccountCreateRequestDTO.builder()
                .userId("user-1")
                .accountName("Primary")
                .openingBalance(BigDecimal.ZERO)
                .currency("ZZZ")
                .build();

        assertThrows(IllegalArgumentException.class, () -> accountService.create(USER_ID, request));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void listsAndFindsAccountsAsDtos() {
        Account account = account(1L, BigDecimal.ZERO, "INR");
        when(accountRepository.findAllByUserIdOrderByAccountNameAsc(USER_ID)).thenReturn(List.of(account));
        when(accountRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(account));

        assertEquals(1, accountService.findAll(USER_ID).size());
        assertEquals("Primary", accountService.findById(USER_ID, 1L).getAccountName());
    }

    @Test
    void reportsMissingAccount() {
        when(accountRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.findById(USER_ID, 99L));
    }

    @Test
    void doesNotReturnAnAccountOwnedByAnotherUser() {
        when(accountRepository.findByIdAndUserId(1L, "user-2")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.findById("user-2", 1L));
        verify(accountRepository).findByIdAndUserId(1L, "user-2");
    }

    @Test
    void updatesNameAndAllowsCurrencyChangeOnlyForEmptyAccount() {
        Account account = account(1L, BigDecimal.ZERO, "INR");
        when(accountRepository.findByIdAndUserIdForUpdate(1L, USER_ID)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccount_Id(1L)).thenReturn(false);
        when(accountRepository.save(account)).thenReturn(account);

        AccountResponseDTO response = accountService.update(USER_ID, 1L, AccountUpdateRequestDTO.builder()
                .accountName("Holiday Fund")
                .currency("usd")
                .build());

        assertEquals("Holiday Fund", response.getAccountName());
        assertEquals("USD", response.getCurrency());
    }

    @Test
    void rejectsCurrencyChangeWhenAccountHasLedgerHistory() {
        Account account = account(1L, BigDecimal.ZERO, "INR");
        when(accountRepository.findByIdAndUserIdForUpdate(1L, USER_ID)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccount_Id(1L)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> accountService.update(USER_ID, 1L,
                AccountUpdateRequestDTO.builder().accountName("Primary").currency("USD").build()));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void rejectsCurrencyChangeWhenBalanceIsNotZero() {
        Account account = account(1L, new BigDecimal("10.00"), "INR");
        when(accountRepository.findByIdAndUserIdForUpdate(1L, USER_ID)).thenReturn(Optional.of(account));

        assertThrows(ResourceConflictException.class, () -> accountService.update(USER_ID, 1L,
                AccountUpdateRequestDTO.builder().accountName("Primary").currency("USD").build()));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void rejectsDeleteWhenTransactionsReferenceAccount() {
        Account account = account(1L, BigDecimal.ZERO, "INR");
        when(accountRepository.findByIdAndUserIdForUpdate(1L, USER_ID)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccount_Id(1L)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> accountService.delete(USER_ID, 1L));
        verify(accountRepository, never()).delete(any());
    }

    @Test
    void deletesAccountWithoutTransactions() {
        Account account = account(1L, BigDecimal.ZERO, "INR");
        when(accountRepository.findByIdAndUserIdForUpdate(1L, USER_ID)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccount_Id(1L)).thenReturn(false);

        accountService.delete(USER_ID, 1L);

        verify(accountRepository).delete(account);
    }

    private Account account(Long id, BigDecimal balance, String currency) {
        return Account.builder()
                .id(id)
                .userId("user-1")
                .accountName("Primary")
                .balance(balance)
                .currency(currency)
                .createdAt(LocalDateTime.of(2026, 11, 25, 10, 0))
                .build();
    }
}
