package com.lifemate.expense.controller;

import com.lifemate.expense.dto.AccountCreateRequestDTO;
import com.lifemate.expense.dto.AccountResponseDTO;
import com.lifemate.expense.dto.AccountUpdateRequestDTO;
import com.lifemate.expense.service.AccountService;
import com.lifemate.expense.config.RequestHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponseDTO> create(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @Valid @RequestBody AccountCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(userId, request));
    }

    @GetMapping
    public List<AccountResponseDTO> findAll(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId) {
        return accountService.findAll(userId);
    }

    @GetMapping("/{id}")
    public AccountResponseDTO findById(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @PathVariable Long id) {
        return accountService.findById(userId, id);
    }

    @PutMapping("/{id}")
    public AccountResponseDTO update(
            @PathVariable Long id,
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @Valid @RequestBody AccountUpdateRequestDTO request) {
        return accountService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(RequestHeaders.AUTHENTICATED_USER) String userId,
            @PathVariable Long id) {
        accountService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
