package com.lifemate.expense.controller;

import com.lifemate.expense.dto.CategoryBudgetRequestDTO;
import com.lifemate.expense.dto.CategoryBudgetResponseDTO;
import com.lifemate.expense.exception.GlobalExceptionHandler;
import com.lifemate.expense.exception.ResourceConflictException;
import com.lifemate.expense.service.CategoryBudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryBudgetControllerTest {

    private CategoryBudgetService categoryBudgetService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        categoryBudgetService = mock(CategoryBudgetService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoryBudgetController(categoryBudgetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsInvalidCategoryBudget() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .header("X-Authenticated-User", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "monthlyBudgetLimit": -0.001
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.monthlyBudgetLimit").exists());
    }

    @Test
    void createsValidCategoryBudget() throws Exception {
        when(categoryBudgetService.create(eq("user-1"), any(CategoryBudgetRequestDTO.class)))
                .thenReturn(CategoryBudgetResponseDTO.builder()
                        .id(9L)
                        .name("Education")
                        .monthlyBudgetLimit(new BigDecimal("3000.00"))
                        .build());

        mockMvc.perform(post("/api/v1/categories")
                        .header("X-Authenticated-User", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Education",
                                  "monthlyBudgetLimit": 3000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.name").value("Education"))
                .andExpect(jsonPath("$.monthlyBudgetLimit").value(3000.00));
    }

    @Test
    void returnsConflictWhenDeletingReferencedCategory() throws Exception {
        doThrow(new ResourceConflictException("Category cannot be deleted while transactions reference it"))
                .when(categoryBudgetService).delete("user-1", 1L);

        mockMvc.perform(delete("/api/v1/categories/1")
                        .header("X-Authenticated-User", "user-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Category cannot be deleted while transactions reference it"));
        verify(categoryBudgetService).delete("user-1", 1L);
    }
}
