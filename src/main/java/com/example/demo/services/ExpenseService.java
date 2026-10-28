
package com.example.demo.services;

import com.example.demo.dto.ExpenseRequest;
import com.example.demo.model.AppUser;
import com.example.demo.model.Expense;
import com.example.demo.repository.ExpenseRepository;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    // CREATE
    @Transactional
    public Expense createExpense(
            ExpenseRequest request,
            UUID userId) {

        AppUser user = userRepository.findById(userId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User not found"
                )
            );

        Expense expense = Expense.builder()
            .title(request.title())
            .amount(request.amount())
            .user(user)
            .build();

        return expenseRepository.save(expense);
    }

    // GET ALL
    @Transactional(readOnly = true)
    public List<Expense> getAllExpenses(UUID userId) {

        return expenseRepository
            .findAllByUser_IdOrderByCreatedAtDesc(userId);
    }

    // GET BY ID
    @Transactional(readOnly = true)
    public Expense getExpenseById(
            UUID expenseId,
            UUID userId) {

        return findOwnedExpense(expenseId, userId);
    }

    // UPDATE
    @Transactional
    public Expense updateExpense(
            UUID expenseId,
            ExpenseRequest request,
            UUID userId) {

        Expense expense = findOwnedExpense(
            expenseId, userId
        );

        expense.setTitle(request.title());
        expense.setAmount(request.amount());

        return expenseRepository.save(expense);
    }

    // DELETE
    @Transactional
    public void deleteExpense(
            UUID expenseId,
            UUID userId) {

        Expense expense = findOwnedExpense(
            expenseId, userId
        );

        expenseRepository.delete(expense);
    }

    // Only return expenses owned by logged-in user
    private Expense findOwnedExpense(
            UUID expenseId,
            UUID userId) {

        return expenseRepository
            .findByIdAndUser_Id(expenseId, userId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Expense not found"
                )
            );
    }
}
