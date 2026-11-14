package com.lifemate.expense.service;

import com.lifemate.expense.dto.ExpenseRequestDTO;
import com.lifemate.expense.dto.ExpenseResponseDTO;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.entity.Transaction;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.repository.AccountRepository;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ExpenseResponseDTO createExpense(ExpenseRequestDTO request) {
        log.info("Creating expense for account ID: {} with amount: {}", request.getAccountId(), request.getAmount());

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + request.getAccountId()));

        Category category = categoryRepository.findByNameIgnoreCase(request.getCategoryName())
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(request.getCategoryName())
                        .monthlyBudgetLimit(new java.math.BigDecimal("5000.00")) // Default limit
                        .build()));

        Transaction transaction = Transaction.builder()
                .account(account)
                .category(category)
                .amount(request.getAmount())
                .transactionType(request.getTransactionType() != null ? request.getTransactionType() : "EXPENSE")
                .merchant(request.getMerchant())
                .rawInput(request.getRawInput())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Successfully saved transaction with ID: {}", savedTransaction.getId());

        return mapToResponseDTO.apply(savedTransaction);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponseDTO> getAllExpenses() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this.mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private final java.util.function.Function<Transaction, ExpenseResponseDTO> mapToResponseDTO = tx -> ExpenseResponseDTO.builder()
            .id(tx.getId())
            .accountId(tx.getAccount() != null ? tx.getAccount().getId() : null)
            .accountName(tx.getAccount() != null ? tx.getAccount().getAccountName() : null)
            .categoryId(tx.getCategory() != null ? tx.getCategory().getId() : null)
            .categoryName(tx.getCategory() != null ? tx.getCategory().getName() : null)
            .amount(tx.getAmount())
            .transactionType(tx.getTransactionType())
            .merchant(tx.getMerchant())
            .rawInput(tx.getRawInput())
            .createdAt(tx.getCreatedAt())
            .build();
}