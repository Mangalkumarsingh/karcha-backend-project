
package com.example.demo.controller;

import com.example.demo.dto.ExpenseRequest;
import com.example.demo.model.Expense;
import com.example.demo.services.ExpenseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    // 1. Add Expense - existing API
    @PostMapping
    public ResponseEntity<Expense> addExpense(
            @Valid @RequestBody ExpenseRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Expense saved = expenseService.createExpense(
            request, getUserId(jwt)
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(saved);
    }

    // 2. Get all expenses for logged-in user
    @GetMapping
    public ResponseEntity<List<Expense>> getAllExpenses(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
            expenseService.getAllExpenses(getUserId(jwt))
        );
    }

    // 3. Get single expense
    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpenseById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
            expenseService.getExpenseById(id, getUserId(jwt))
        );
    }

    // 4. Update expense
    @PutMapping("/{id}")
    public ResponseEntity<Expense> updateExpense(
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
            expenseService.updateExpense(
                id, request, getUserId(jwt)
            )
        );
    }

    // 5. Delete expense
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        expenseService.deleteExpense(id, getUserId(jwt));

        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required"
            );
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid token subject"
            );
        }
    }
}
