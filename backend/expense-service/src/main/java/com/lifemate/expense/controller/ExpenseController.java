package com.lifemate.expense.controller;

import com.lifemate.expense.dto.*;
import com.lifemate.expense.config.RequestHeaders;
import com.lifemate.expense.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponseDTO> createExpense(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @Valid @RequestBody ExpenseRequestDTO request) {
        ExpenseResponseDTO created = expenseService.createExpense(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/parse")
    public ResponseEntity<ExpenseResponseDTO> createFromNaturalLanguage(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @Valid @RequestBody NaturalLanguageExpenseRequestDTO request) {
        ExpenseResponseDTO created = expenseService.createFromNaturalLanguage(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/webhook/sms")
    public ResponseEntity<ExpenseResponseDTO> receiveSmsWebhook(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @Valid @RequestBody SmsWebhookRequestDTO request) {
        ExpenseResponseDTO created = expenseService.processSmsWebhook(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponseDTO>> getAllExpenses(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId) {
        List<ExpenseResponseDTO> expenses = expenseService.getAllExpenses(userId);
        return ResponseEntity.ok(expenses);
    }

    @GetMapping("/{transactionId}")
    public ExpenseResponseDTO getExpense(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @PathVariable Long transactionId) {
        return expenseService.getExpense(userId, transactionId);
    }

    @PutMapping("/{transactionId}")
    public ExpenseResponseDTO updateExpense(
            @PathVariable Long transactionId,
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @Valid @RequestBody ExpenseRequestDTO request) {
        return expenseService.updateExpense(userId, transactionId, request);
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteExpense(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @PathVariable Long transactionId) {
        expenseService.deleteExpense(userId, transactionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/webhook/sms", consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ExpenseResponseDTO> ingestSmsForm(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @RequestParam("sender") String sender,
            @RequestParam("body") String body) {
        SmsWebhookRequestDTO request = new SmsWebhookRequestDTO();
        request.setSender(sender);
        request.setBody(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.processSmsWebhook(userId, request));
    }
}
