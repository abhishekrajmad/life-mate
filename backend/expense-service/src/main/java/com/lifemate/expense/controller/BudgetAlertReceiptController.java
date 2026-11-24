package com.lifemate.expense.controller;

import com.lifemate.expense.dto.BudgetAlertReceiptRequestDTO;
import com.lifemate.expense.dto.BudgetAlertReceiptResponseDTO;
import com.lifemate.expense.service.BudgetAlertReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budget-alerts")
@RequiredArgsConstructor
public class BudgetAlertReceiptController {
    private final BudgetAlertReceiptService service;

    @PostMapping
    public BudgetAlertReceiptResponseDTO record(@Valid @RequestBody BudgetAlertReceiptRequestDTO request) {
        return service.record(request);
    }
}
