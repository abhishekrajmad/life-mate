package com.lifemate.expense.service;

import com.lifemate.expense.dto.CategoryBudgetStatusDTO;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class CategoryBudgetService {
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public CategoryBudgetStatusDTO getMonthlyStatus(Long categoryId, YearMonth month, Long throughTransactionId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));
        BigDecimal spent = transactionRepository.sumExpensesThroughTransaction(
                categoryId, month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay(), throughTransactionId);
        return new CategoryBudgetStatusDTO(categoryId, category.getName(),
                category.getMonthlyBudgetLimit() == null ? BigDecimal.ZERO : category.getMonthlyBudgetLimit(), spent);
    }
}
