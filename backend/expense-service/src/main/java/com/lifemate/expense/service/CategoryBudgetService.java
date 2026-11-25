package com.lifemate.expense.service;

import com.lifemate.expense.dto.CategoryBudgetStatusDTO;
import com.lifemate.expense.dto.CategoryBudgetRequestDTO;
import com.lifemate.expense.dto.CategoryBudgetResponseDTO;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.exception.ResourceConflictException;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryBudgetService {
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public CategoryBudgetResponseDTO create(String userId, CategoryBudgetRequestDTO request) {
        String owner = normalizeUserId(userId);
        String name = normalizeName(request.getName());
        ensureNameAvailable(owner, name, null);
        Category category = Category.builder()
                .userId(owner)
                .name(name)
                .monthlyBudgetLimit(request.getMonthlyBudgetLimit())
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryBudgetResponseDTO> findAll(String userId) {
        return categoryRepository.findAllByUserIdOrderByNameAsc(normalizeUserId(userId)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryBudgetResponseDTO findById(String userId, Long id) {
        return toResponse(requireCategory(userId, id));
    }

    @Transactional
    public CategoryBudgetResponseDTO update(String userId, Long id, CategoryBudgetRequestDTO request) {
        String owner = normalizeUserId(userId);
        Category category = requireCategory(owner, id);
        String name = normalizeName(request.getName());
        ensureNameAvailable(owner, name, id);
        category.setName(name);
        category.setMonthlyBudgetLimit(request.getMonthlyBudgetLimit());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(String userId, Long id) {
        Category category = requireCategory(userId, id);
        if (transactionRepository.existsByCategory_Id(id)) {
            throw new ResourceConflictException("Category cannot be deleted while transactions reference it");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public CategoryBudgetStatusDTO getMonthlyStatus(
            String userId, Long categoryId, YearMonth month, Long throughTransactionId) {
        String owner = normalizeUserId(userId);
        Category category = requireCategory(owner, categoryId);
        BigDecimal spent = transactionRepository.sumExpensesThroughTransaction(
                categoryId, owner, month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay(), throughTransactionId);
        return new CategoryBudgetStatusDTO(categoryId, category.getName(),
                category.getMonthlyBudgetLimit() == null ? BigDecimal.ZERO : category.getMonthlyBudgetLimit(), spent);
    }

    private Category requireCategory(String userId, Long id) {
        return categoryRepository.findByIdAndUserId(id, normalizeUserId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private void ensureNameAvailable(String userId, String name, Long currentCategoryId) {
        Optional<Category> existing = categoryRepository.findByUserIdAndNameIgnoreCase(userId, name);
        if (existing.isPresent() && !existing.get().getId().equals(currentCategoryId)) {
            throw new ResourceConflictException("Category name already exists: " + name);
        }
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 50) {
            throw new IllegalArgumentException("Authenticated user ID must contain between 1 and 50 characters");
        }
        return userId.trim();
    }

    private CategoryBudgetResponseDTO toResponse(Category category) {
        return CategoryBudgetResponseDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .monthlyBudgetLimit(category.getMonthlyBudgetLimit() == null
                        ? BigDecimal.ZERO : category.getMonthlyBudgetLimit())
                .build();
    }
}
