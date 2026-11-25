package com.lifemate.expense.service;

import com.lifemate.expense.dto.AccountCreateRequestDTO;
import com.lifemate.expense.dto.AccountResponseDTO;
import com.lifemate.expense.dto.AccountUpdateRequestDTO;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.exception.ResourceConflictException;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.repository.AccountRepository;
import com.lifemate.expense.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public AccountResponseDTO create(String userId, AccountCreateRequestDTO request) {
        Account account = Account.builder()
                .userId(normalizeUserId(userId))
                .accountName(request.getAccountName().trim())
                .balance(request.getOpeningBalance())
                .currency(normalizeCurrency(request.getCurrency()))
                .build();
        return toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponseDTO> findAll(String userId) {
        return accountRepository.findAllByUserIdOrderByAccountNameAsc(normalizeUserId(userId)).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponseDTO findById(String userId, Long id) {
        return toResponse(requireAccount(userId, id));
    }

    @Transactional
    public AccountResponseDTO update(String userId, Long id, AccountUpdateRequestDTO request) {
        Account account = requireAccountForUpdate(userId, id);
        String requestedCurrency = normalizeCurrency(request.getCurrency());
        if (!requestedCurrency.equalsIgnoreCase(account.getCurrency())
                && (account.getBalance().compareTo(BigDecimal.ZERO) != 0
                || transactionRepository.existsByAccount_Id(id))) {
            throw new ResourceConflictException(
                    "Currency can only be changed when the account has a zero balance and no transactions");
        }

        account.setAccountName(request.getAccountName().trim());
        account.setCurrency(requestedCurrency);
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public void delete(String userId, Long id) {
        Account account = requireAccountForUpdate(userId, id);
        if (transactionRepository.existsByAccount_Id(id)) {
            throw new ResourceConflictException("Account cannot be deleted while transactions reference it");
        }
        accountRepository.delete(account);
    }

    private Account requireAccount(String userId, Long id) {
        return accountRepository.findByIdAndUserId(id, normalizeUserId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));
    }

    private Account requireAccountForUpdate(String userId, Long id) {
        return accountRepository.findByIdAndUserIdForUpdate(id, normalizeUserId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 50) {
            throw new IllegalArgumentException("Authenticated user ID must contain between 1 and 50 characters");
        }
        return userId.trim();
    }

    private String normalizeCurrency(String currencyCode) {
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(normalized).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported ISO 4217 currency code: " + normalized);
        }
    }

    private AccountResponseDTO toResponse(Account account) {
        return AccountResponseDTO.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .accountName(account.getAccountName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
