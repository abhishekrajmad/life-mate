package com.lifemate.expense.controller;

import com.lifemate.expense.dto.CategoryBudgetStatusDTO;
import com.lifemate.expense.service.CategoryBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryBudgetController {
    private final CategoryBudgetService categoryBudgetService;

    @GetMapping("/{categoryId}/budget-status")
    public CategoryBudgetStatusDTO getBudgetStatus(@PathVariable Long categoryId,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                                    @RequestParam Long throughTransactionId) {
        return categoryBudgetService.getMonthlyStatus(categoryId, month, throughTransactionId);
    }
}
