package com.lifemate.expense.service;

import com.lifemate.expense.dto.CategoryBudgetRequestDTO;
import com.lifemate.expense.dto.CategoryBudgetResponseDTO;
import com.lifemate.expense.dto.CategoryBudgetStatusDTO;
import com.lifemate.expense.entity.Category;
import com.lifemate.expense.exception.ResourceConflictException;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.repository.CategoryRepository;
import com.lifemate.expense.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryBudgetServiceTest {

    private static final String USER_ID = "user-1";

    @Mock CategoryRepository categoryRepository;
    @Mock TransactionRepository transactionRepository;
    @InjectMocks CategoryBudgetService categoryBudgetService;

    @Test
    void createsCategoryWithTrimmedNameAndBudget() {
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Education"))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(9L);
            return category;
        });

        CategoryBudgetResponseDTO response = categoryBudgetService.create(
                USER_ID, request(" Education ", "3000.00"));

        assertEquals(9L, response.getId());
        assertEquals("Education", response.getName());
        assertEquals(new BigDecimal("3000.00"), response.getMonthlyBudgetLimit());
    }

    @Test
    void rejectsCaseInsensitiveDuplicateName() {
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER_ID, "food & dining"))
                .thenReturn(Optional.of(category(1L, "Food & Dining", "8000.00")));

        assertThrows(ResourceConflictException.class,
                () -> categoryBudgetService.create(USER_ID, request("food & dining", "5000.00")));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void listsCategoriesInRepositoryOrder() {
        when(categoryRepository.findAllByUserIdOrderByNameAsc(USER_ID)).thenReturn(List.of(
                category(2L, "Education", "3000.00"),
                category(1L, "Food & Dining", "8000.00")));

        List<CategoryBudgetResponseDTO> response = categoryBudgetService.findAll(USER_ID);

        assertEquals(List.of("Education", "Food & Dining"),
                response.stream().map(CategoryBudgetResponseDTO::getName).toList());
    }

    @Test
    void reportsMissingCategory() {
        when(categoryRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryBudgetService.findById(USER_ID, 99L));
    }

    @Test
    void allowsDifferentUsersToUseTheSameCategoryName() {
        String anotherUser = "user-2";
        when(categoryRepository.findByUserIdAndNameIgnoreCase(anotherUser, "Education"))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            assertEquals(anotherUser, category.getUserId());
            category.setId(10L);
            return category;
        });

        CategoryBudgetResponseDTO response = categoryBudgetService.create(
                anotherUser, request("Education", "1000.00"));

        assertEquals("Education", response.getName());
    }

    @Test
    void updatesCategoryAndAllowsItsCurrentName() {
        Category category = category(1L, "Food & Dining", "8000.00");
        when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Food & Dining"))
                .thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryBudgetResponseDTO response = categoryBudgetService.update(
                USER_ID, 1L, request("Food & Dining", "9000.00"));

        assertEquals(new BigDecimal("9000.00"), response.getMonthlyBudgetLimit());
    }

    @Test
    void rejectsRenameToAnotherCategoryName() {
        Category category = category(1L, "Food & Dining", "8000.00");
        Category existing = category(2L, "Education", "3000.00");
        when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Education"))
                .thenReturn(Optional.of(existing));

        assertThrows(ResourceConflictException.class,
                () -> categoryBudgetService.update(USER_ID, 1L, request("Education", "9000.00")));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void keepsBudgetStatusQueryCompatibleWithNotificationFlow() {
        Category category = category(1L, "Food & Dining", "8000.00");
        when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(category));
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 1, 0, 0);
        when(transactionRepository.sumExpensesThroughTransaction(1L, USER_ID, start, end, 25L))
                .thenReturn(new BigDecimal("8100.00"));

        CategoryBudgetStatusDTO status = categoryBudgetService.getMonthlyStatus(
                USER_ID, 1L, YearMonth.of(2026, 11), 25L);

        assertEquals(new BigDecimal("8000.00"), status.getMonthlyLimit());
        assertEquals(new BigDecimal("8100.00"), status.getSpent());
    }

    @Test
    void rejectsDeleteWhenTransactionsReferenceCategory() {
        Category category = category(1L, "Food & Dining", "8000.00");
        when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(category));
        when(transactionRepository.existsByCategory_Id(1L)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> categoryBudgetService.delete(USER_ID, 1L));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deletesUnusedCategory() {
        Category category = category(9L, "Education", "3000.00");
        when(categoryRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(category));
        when(transactionRepository.existsByCategory_Id(9L)).thenReturn(false);

        categoryBudgetService.delete(USER_ID, 9L);

        verify(categoryRepository).delete(category);
    }

    private CategoryBudgetRequestDTO request(String name, String limit) {
        return CategoryBudgetRequestDTO.builder()
                .name(name)
                .monthlyBudgetLimit(new BigDecimal(limit))
                .build();
    }

    private Category category(Long id, String name, String limit) {
        return Category.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .monthlyBudgetLimit(new BigDecimal(limit))
                .build();
    }
}
