package com.lifemate.expense.controller;

import com.lifemate.expense.dto.AccountCreateRequestDTO;
import com.lifemate.expense.dto.AccountResponseDTO;
import com.lifemate.expense.exception.GlobalExceptionHandler;
import com.lifemate.expense.exception.ResourceConflictException;
import com.lifemate.expense.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest {

    private AccountService accountService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Authenticated-User", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "",
                                  "accountName": "Primary",
                                  "openingBalance": 0.001,
                                  "currency": "RUPEES"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.userId").exists())
                .andExpect(jsonPath("$.details.openingBalance").exists())
                .andExpect(jsonPath("$.details.currency").exists());
    }

    @Test
    void createsValidAccount() throws Exception {
        when(accountService.create(eq("user-1"), any(AccountCreateRequestDTO.class)))
                .thenReturn(AccountResponseDTO.builder()
                .id(1L)
                .userId("user-1")
                .accountName("Primary")
                .balance(new BigDecimal("100.00"))
                .currency("INR")
                .build());

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Authenticated-User", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Primary",
                                  "openingBalance": 100.00,
                                  "currency": "INR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void rejectsDirectBalanceOrOwnerChanges() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/1")
                        .header("X-Authenticated-User", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "different-user",
                                  "accountName": "Primary",
                                  "currency": "INR",
                                  "balance": 999.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.userId").exists())
                .andExpect(jsonPath("$.details.balance").exists());
    }

    @Test
    void returnsConflictWhenDeletingAccountWithTransactions() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceConflictException(
                        "Account cannot be deleted while transactions reference it"))
                .when(accountService).delete("user-1", 1L);

        mockMvc.perform(delete("/api/v1/accounts/1")
                        .header("X-Authenticated-User", "user-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Account cannot be deleted while transactions reference it"));
        verify(accountService).delete("user-1", 1L);
    }
}
