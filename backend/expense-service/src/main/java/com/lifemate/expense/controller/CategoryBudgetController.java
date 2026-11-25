package com.lifemate.expense.controller;

import com.lifemate.expense.dto.CategoryBudgetStatusDTO;
import com.lifemate.expense.dto.CategoryBudgetRequestDTO;
import com.lifemate.expense.dto.CategoryBudgetResponseDTO;
import com.lifemate.expense.service.CategoryBudgetService;
import com.lifemate.expense.config.RequestHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryBudgetController {
    private final CategoryBudgetService categoryBudgetService;

    @PostMapping
    public ResponseEntity<CategoryBudgetResponseDTO> create(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @Valid @RequestBody CategoryBudgetRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryBudgetService.create(userId, request));
    }

    @GetMapping
    public List<CategoryBudgetResponseDTO> findAll(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId) {
        return categoryBudgetService.findAll(userId);
    }

    @GetMapping("/{categoryId}")
    public CategoryBudgetResponseDTO findById(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @PathVariable Long categoryId) {
        return categoryBudgetService.findById(userId, categoryId);
    }

    @PutMapping("/{categoryId}")
    public CategoryBudgetResponseDTO update(
            @PathVariable Long categoryId,
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @Valid @RequestBody CategoryBudgetRequestDTO request) {
        return categoryBudgetService.update(userId, categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @PathVariable Long categoryId) {
        categoryBudgetService.delete(userId, categoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{categoryId}/budget-status")
    public CategoryBudgetStatusDTO getBudgetStatus(@PathVariable Long categoryId,
                                                    @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                                    @RequestParam Long throughTransactionId) {
        return categoryBudgetService.getMonthlyStatus(userId, categoryId, month, throughTransactionId);
    }
}
