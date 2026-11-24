package com.lifemate.expense.controller;

import com.lifemate.expense.dto.DashboardSummaryDTO;
import com.lifemate.expense.dto.DashboardSummaryDTO.CategoryBudgetStatus;
import com.lifemate.expense.dto.ExpenseResponseDTO;
import com.lifemate.expense.entity.Account;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.repository.AccountRepository;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    @GetMapping("/summary")
    public DashboardSummaryDTO getDashboardSummary() {
        BigDecimal totalBalance = accountRepository.findAll().stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        List<Category> categories = categoryRepository.findAll();

        List<CategoryBudgetStatus> categoryStatuses = categories.stream().map(cat -> {
            BigDecimal spent = transactionRepository.sumMonthlyExpensesByCategory(cat.getId(), startOfMonth);
            BigDecimal limit = cat.getMonthlyBudgetLimit() != null ? cat.getMonthlyBudgetLimit() : BigDecimal.ZERO;

            double pct = 0.0;
            if (limit.compareTo(BigDecimal.ZERO) > 0) {
                pct = spent.divide(limit, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            return CategoryBudgetStatus.builder()
                    .categoryId(cat.getId())
                    .categoryName(cat.getName())
                    .monthlyLimit(limit)
                    .spent(spent)
                    .percentageUsed(Math.round(pct * 10.0) / 10.0)
                    .exceeded(limit.compareTo(BigDecimal.ZERO) > 0 && spent.compareTo(limit) > 0)
                    .build();
        }).toList();

        BigDecimal totalSpent = categoryStatuses.stream()
                .map(CategoryBudgetStatus::getSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudget = categoryStatuses.stream()
                .map(CategoryBudgetStatus::getMonthlyLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ExpenseResponseDTO> recent = transactionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .limit(15)
                .map(tx -> ExpenseResponseDTO.builder()
                        .id(tx.getId())
                        .accountId(tx.getAccount().getId())
                        .accountName(tx.getAccount().getAccountName())
                        .categoryId(tx.getCategory().getId())
                        .categoryName(tx.getCategory().getName())
                        .amount(tx.getAmount())
                        .transactionType(tx.getTransactionType())
                        .merchant(tx.getMerchant())
                        .rawInput(tx.getRawInput())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .toList();

        return DashboardSummaryDTO.builder()
                .totalBalance(totalBalance)
                .totalMonthlySpent(totalSpent)
                .totalMonthlyBudget(totalBudget)
                .categoryBudgets(categoryStatuses)
                .recentTransactions(recent)
                .build();
    }
}