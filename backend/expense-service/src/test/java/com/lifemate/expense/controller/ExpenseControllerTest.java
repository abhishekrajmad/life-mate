package com.lifemate.expense.controller;

import com.lifemate.expense.exception.GlobalExceptionHandler;
import com.lifemate.expense.exception.AiParsingUnavailableException;
import com.lifemate.expense.exception.NonTransactionMessageException;
import com.lifemate.expense.exception.ResourceNotFoundException;
import com.lifemate.expense.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseControllerTest {

    private ExpenseService expenseService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        expenseService = mock(ExpenseService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ExpenseController(expenseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsInvalidTransactionUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/expenses/10")
                .header("X-Authenticated-User", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "accountId": 0,
                          "categoryName": "Food & Dining",
                          "amount": 0.001,
                          "transactionType": "TRANSFER"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.accountId").exists())
                .andExpect(jsonPath("$.details.amount").exists())
                .andExpect(jsonPath("$.details.transactionType").exists());
    }

    @Test
    void deletesTransaction() throws Exception {
        mockMvc.perform(delete("/api/v1/expenses/10")
                .header("X-Authenticated-User", "user-1"))
                .andExpect(status().isNoContent());

        verify(expenseService).deleteExpense("user-1", 10L);
    }

    @Test
    void returnsNotFoundForMissingTransaction() throws Exception {
        when(expenseService.getExpense("user-1", 99L))
                .thenThrow(new ResourceNotFoundException("Transaction not found with ID: 99"));

        mockMvc.perform(get("/api/v1/expenses/99")
                .header("X-Authenticated-User", "user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found with ID: 99"));
    }

    @Test
    void reportsManualFallbackRequiredWhenAiParsingIsUnavailable() throws Exception {
        when(expenseService.createFromNaturalLanguage(eq("user-1"), any()))
                .thenThrow(new AiParsingUnavailableException(new IllegalStateException("invalid AI response")));

        mockMvc.perform(post("/api/v1/expenses/parse")
                .header("X-Authenticated-User", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rawText":"coffee purchase"}
                        """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.details.manualFallbackRequired").value("true"))
                .andExpect(
                        jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no transaction was saved")));
    }

    @Test
    void validatesManualFallbackBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/expenses/parse")
                .header("X-Authenticated-User", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "rawText": "coffee purchase",
                          "manualFallback": {
                            "amount": -5,
                            "categoryName": ""
                          }
                        }
                        """))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verify(expenseService, org.mockito.Mockito.never())
                .createFromNaturalLanguage(eq("user-1"), any());
    }

    @Test
    void returnsUnprocessableEntityForNonTransactionSms() throws Exception {
        when(expenseService.processSmsWebhook(eq("user-1"), any()))
                .thenThrow(new NonTransactionMessageException());

        mockMvc.perform(post("/api/v1/expenses/webhook/sms")
                .header("X-Authenticated-User", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sender":"BANK","body":"Your OTP is 123456"}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no transaction was saved")));
    }
}
