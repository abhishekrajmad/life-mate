package com.lifemate.expense.controller;

import com.lifemate.expense.dto.*;
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
    public ResponseEntity<ExpenseResponseDTO> createExpense(@Valid @RequestBody ExpenseRequestDTO request) {
        ExpenseResponseDTO created = expenseService.createExpense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/parse")
    public ResponseEntity<ExpenseResponseDTO> createFromNaturalLanguage(
            @Valid @RequestBody NaturalLanguageExpenseRequestDTO request) {
        ExpenseResponseDTO created = expenseService.createFromNaturalLanguage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/webhook/sms")
    public ResponseEntity<ExpenseResponseDTO> receiveSmsWebhook(
            @Valid @RequestBody SmsWebhookRequestDTO request) {
        ExpenseResponseDTO created = expenseService.processSmsWebhook(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponseDTO>> getAllExpenses() {
        List<ExpenseResponseDTO> expenses = expenseService.getAllExpenses();
        return ResponseEntity.ok(expenses);
    }
}