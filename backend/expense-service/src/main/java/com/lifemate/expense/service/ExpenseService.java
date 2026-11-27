package com.lifemate.expense.service;

import com.lifemate.expense.client.AiAgentClient;
import com.lifemate.expense.dto.*;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.entity.Transaction;
import com.lifemate.expense.exception.AiParsingUnavailableException;
import com.lifemate.expense.exception.NonTransactionMessageException;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.messaging.ExpenseOutboxWriter;
import com.lifemate.expense.repository.AccountRepository;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final String EXPENSE = "EXPENSE";
    private static final String INCOME = "INCOME";
    private static final String LEDGER_CURRENCY = "INR";
    private static final Set<String> SUPPORTED_CATEGORIES = Set.of(
            "Food & Dining",
            "Groceries",
            "Transport",
            "Utilities & Bills",
            "Shopping",
            "Entertainment",
            "Health & Fitness",
            "Other");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AiAgentClient aiAgentClient;
    private final ExpenseOutboxWriter outboxWriter;

    @Transactional
    public ExpenseResponseDTO createExpense(String userId, ExpenseRequestDTO request) {
        log.info("Creating expense for account ID: {} with amount: {}", request.getAccountId(), request.getAmount());

        String owner = normalizeUserId(userId);
        normalizeSupportedCategoryName(request.getCategoryName());
        validateLedgerAmount(request.getAmount());
        Account account = requireAccountForUpdate(owner, request.getAccountId());
        validateAccountCurrency(account);
        Category category = resolveCategory(owner, request.getCategoryName());
        String txType = normalizeTransactionType(request.getTransactionType());

        applyBalanceEffect(account, txType, request.getAmount());
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .account(account)
                .category(category)
                .amount(request.getAmount())
                .transactionType(txType)
                .merchant(trimToNull(request.getMerchant()))
                .rawInput(request.getRawInput())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        outboxWriter.save(ExpenseCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .transactionId(savedTransaction.getId())
                .userId(account.getUserId())
                .accountId(account.getId())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .amount(savedTransaction.getAmount())
                .transactionType(savedTransaction.getTransactionType())
                .timestamp(savedTransaction.getCreatedAt())
                .build());
        log.info("Successfully saved transaction with ID: {}", savedTransaction.getId());

        return mapToResponseDTO(savedTransaction);
    }

    @Transactional
    public ExpenseResponseDTO createFromNaturalLanguage(String userId, NaturalLanguageExpenseRequestDTO request) {
        Long targetAccountId = request.getAccountId() != null ? request.getAccountId() : 1L;
        AiParseResponseDTO parsed = parseExpenseOrFallback(
                request.getRawText(), request.getCurrency(), request.getManualFallback());
        if ("NON_TRANSACTION".equals(parsed.getTransactionType())) {
            throw new NonTransactionMessageException();
        }

        ExpenseRequestDTO expenseRequest = ExpenseRequestDTO.builder()
                .accountId(targetAccountId)
                .categoryName(parsed.getCategory())
                .amount(parsed.getAmount())
                .transactionType("EXPENSE")
                .merchant(parsed.getMerchant())
                .rawInput(request.getRawText())
                .build();

        return createExpense(userId, expenseRequest);
    }

    @Transactional
    public ExpenseResponseDTO processSmsWebhook(String userId, SmsWebhookRequestDTO smsRequest) {
        log.info("Received SMS webhook from sender: {}", smsRequest.getSender());
        Long targetAccountId = smsRequest.getAccountId() != null ? smsRequest.getAccountId() : 1L;

        AiParseResponseDTO parsed = parseExpenseOrFallback(
                smsRequest.getBody(), "INR", smsRequest.getManualFallback());
        if ("NON_TRANSACTION".equals(parsed.getTransactionType())) {
            throw new NonTransactionMessageException();
        }

        ExpenseRequestDTO expenseRequest = ExpenseRequestDTO.builder()
                .accountId(targetAccountId)
                .categoryName(parsed.getCategory())
                .amount(parsed.getAmount())
                .transactionType(parsed.getTransactionType())
                .merchant(parsed.getMerchant())
                .rawInput("[" + smsRequest.getSender() + "] " + smsRequest.getBody())
                .build();

        return createExpense(userId, expenseRequest);
    }

    private AiParseResponseDTO parseExpenseOrFallback(
            String rawText, String currency, ManualExpenseFallbackDTO manualFallback) {
        String normalizedCurrency = normalizeLedgerCurrency(currency);
        if (manualFallback != null) {
            return AiParseResponseDTO.builder()
                    .amount(manualFallback.getAmount())
                    .category(manualFallback.getCategoryName())
                    .merchant(manualFallback.getMerchant())
                    .currency(normalizedCurrency)
                    .transactionType(manualFallback.getTransactionType() == null
                            ? EXPENSE
                            : normalizeTransactionType(manualFallback.getTransactionType()))
                    .build();
        }

        try {
            AiParseResponseDTO parsed = aiAgentClient.parseExpense(rawText, normalizedCurrency);
            if (parsed == null) {
                throw new IllegalStateException("AI response is missing transaction details");
            }
            String transactionType = normalizeParsedTransactionType(parsed.getTransactionType());
            parsed.setTransactionType(transactionType);
            String parsedCurrency = normalizeLedgerCurrency(
                    parsed.getCurrency() == null ? normalizedCurrency : parsed.getCurrency());
            parsed.setCurrency(parsedCurrency);
            if (!"NON_TRANSACTION".equals(transactionType)
                    && (parsed.getAmount() == null || parsed.getAmount().signum() <= 0
                            || parsed.getAmount().scale() > 2 || parsed.getCategory() == null
                            || parsed.getCategory().isBlank() || parsed.getCategory().length() > 50
                            || !isSupportedCategory(parsed.getCategory()))) {
                throw new IllegalStateException("AI response is missing valid transaction details");
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new AiParsingUnavailableException(exception);
        }
    }

    private String normalizeParsedTransactionType(String transactionType) {
        String normalized = transactionType == null || transactionType.isBlank()
                ? EXPENSE
                : transactionType.trim().toUpperCase(Locale.ROOT);
        if (!EXPENSE.equals(normalized) && !INCOME.equals(normalized)
                && !"NON_TRANSACTION".equals(normalized)) {
            throw new IllegalArgumentException("AI transaction type must be EXPENSE, INCOME, or NON_TRANSACTION");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponseDTO> getAllExpenses(String userId) {
        return transactionRepository.findAllByAccount_UserIdOrderByCreatedAtDesc(normalizeUserId(userId)).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseResponseDTO getExpense(String userId, Long transactionId) {
        return mapToResponseDTO(requireTransaction(userId, transactionId));
    }

    @Transactional
    public ExpenseResponseDTO updateExpense(String userId, Long transactionId, ExpenseRequestDTO request) {
        String owner = normalizeUserId(userId);
        Transaction transaction = requireTransactionForUpdate(owner, transactionId);
        validateLedgerAmount(request.getAmount());
        normalizeSupportedCategoryName(request.getCategoryName());
        Long previousAccountId = transaction.getAccount().getId();
        Map<Long, Account> lockedAccounts = requireAccountsForUpdate(
                owner, previousAccountId, request.getAccountId());
        Account previousAccount = lockedAccounts.get(previousAccountId);
        Account targetAccount = lockedAccounts.get(request.getAccountId());
        validateAccountCurrency(previousAccount);
        validateAccountCurrency(targetAccount);
        Category targetCategory = resolveCategory(owner, request.getCategoryName());
        String targetType = normalizeTransactionType(request.getTransactionType());

        reverseBalanceEffect(previousAccount, transaction.getTransactionType(), transaction.getAmount());
        applyBalanceEffect(targetAccount, targetType, request.getAmount());

        accountRepository.save(previousAccount);
        if (!Objects.equals(previousAccount.getId(), targetAccount.getId())) {
            accountRepository.save(targetAccount);
        }

        transaction.setAccount(targetAccount);
        transaction.setCategory(targetCategory);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType(targetType);
        transaction.setMerchant(trimToNull(request.getMerchant()));
        transaction.setRawInput(request.getRawInput());

        return mapToResponseDTO(transactionRepository.save(transaction));
    }

    @Transactional
    public void deleteExpense(String userId, Long transactionId) {
        String owner = normalizeUserId(userId);
        Transaction transaction = requireTransactionForUpdate(owner, transactionId);
        Account account = requireAccountForUpdate(owner, transaction.getAccount().getId());
        reverseBalanceEffect(account, transaction.getTransactionType(), transaction.getAmount());
        accountRepository.save(account);
        transactionRepository.delete(transaction);
    }

    private Account requireAccountForUpdate(String userId, Long accountId) {
        return accountRepository.findByIdAndUserIdForUpdate(accountId, normalizeUserId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));
    }

    private Map<Long, Account> requireAccountsForUpdate(String userId, Long... accountIds) {
        String owner = normalizeUserId(userId);
        List<Long> orderedIds = Arrays.stream(accountIds)
                .distinct()
                .sorted()
                .toList();
        List<Account> accounts = accountRepository.findAllByUserIdAndIdInForUpdate(owner, orderedIds);
        Map<Long, Account> accountsById = accounts.stream().collect(Collectors.toMap(
                Account::getId,
                account -> account,
                (first, second) -> first,
                LinkedHashMap::new));
        for (Long accountId : orderedIds) {
            if (!accountsById.containsKey(accountId)) {
                throw new ResourceNotFoundException("Account not found with ID: " + accountId);
            }
        }
        return accountsById;
    }

    private Transaction requireTransaction(String userId, Long transactionId) {
        return transactionRepository.findByIdAndAccount_UserId(transactionId, normalizeUserId(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with ID: " + transactionId));
    }

    private Transaction requireTransactionForUpdate(String userId, Long transactionId) {
        return transactionRepository.findByIdAndAccountUserIdForUpdate(
                transactionId, normalizeUserId(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with ID: " + transactionId));
    }

    private Category resolveCategory(String userId, String categoryName) {
        String normalizedName = normalizeSupportedCategoryName(categoryName);
        String owner = normalizeUserId(userId);
        return categoryRepository.findByUserIdAndNameIgnoreCase(owner, normalizedName)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .userId(owner)
                        .name(normalizedName)
                        .monthlyBudgetLimit(new BigDecimal("5000.00"))
                        .build()));
    }

    private String normalizeSupportedCategoryName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        String trimmedName = categoryName.trim();
        for (String supportedCategory : SUPPORTED_CATEGORIES) {
            if (supportedCategory.equalsIgnoreCase(trimmedName)) {
                return supportedCategory;
            }
        }
        throw new IllegalArgumentException("Unsupported category name: " + trimmedName);
    }

    private boolean isSupportedCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return false;
        }
        return SUPPORTED_CATEGORIES.stream()
                .anyMatch(supported -> supported.equalsIgnoreCase(categoryName.trim()));
    }

    private String normalizeLedgerCurrency(String currency) {
        String normalized = currency == null || currency.isBlank()
                ? LEDGER_CURRENCY
                : currency.trim().toUpperCase(Locale.ROOT);
        if (!LEDGER_CURRENCY.equals(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported transaction currency: " + normalized + ". Ledger supports INR only");
        }
        return normalized;
    }

    private void validateAccountCurrency(Account account) {
        String currency = account.getCurrency() == null || account.getCurrency().isBlank()
                ? LEDGER_CURRENCY
                : account.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!LEDGER_CURRENCY.equals(currency)) {
            throw new IllegalArgumentException(
                    "Account currency must be INR; ledger supports INR only");
        }
    }

    private void validateLedgerAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0
                || amount.scale() > 2
                || amount.precision() - Math.max(amount.scale(), 0) > 10) {
            throw new IllegalArgumentException(
                    "Amount must be positive with at most 10 integer and 2 decimal digits");
        }
    }

    private String normalizeTransactionType(String transactionType) {
        String normalized = transactionType == null
                ? EXPENSE
                : transactionType.trim().toUpperCase(Locale.ROOT);
        if (!EXPENSE.equals(normalized) && !INCOME.equals(normalized)) {
            throw new IllegalArgumentException("Transaction type must be EXPENSE or INCOME");
        }
        return normalized;
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 50) {
            throw new IllegalArgumentException("Authenticated user ID must contain between 1 and 50 characters");
        }
        return userId.trim();
    }

    private void applyBalanceEffect(Account account, String transactionType, BigDecimal amount) {
        String normalizedType = normalizeTransactionType(transactionType);
        if (EXPENSE.equals(normalizedType)) {
            account.setBalance(account.getBalance().subtract(amount));
        } else {
            account.setBalance(account.getBalance().add(amount));
        }
    }

    private void reverseBalanceEffect(Account account, String transactionType, BigDecimal amount) {
        String normalizedType = normalizeTransactionType(transactionType);
        if (EXPENSE.equals(normalizedType)) {
            account.setBalance(account.getBalance().add(amount));
        } else {
            account.setBalance(account.getBalance().subtract(amount));
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ExpenseResponseDTO mapToResponseDTO(Transaction tx) {
        return ExpenseResponseDTO.builder()
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

}
