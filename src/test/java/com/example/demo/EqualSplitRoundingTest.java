package com.example.demo;

import com.example.demo.model.AppUser;
import com.example.demo.model.Expense;
import com.example.demo.model.ExpenseSplit;
import com.example.demo.services.SharedExpenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EqualSplitRoundingTest {

    private final SharedExpenseService sharedExpenseService = new SharedExpenseService(
            null, null, null, null, null
    );

    @Test
    @DisplayName("₹100 split among 3 users must allocate ₹33.34, ₹33.33, ₹33.33 totaling exactly ₹100.00")
    void testEqualSplitThreeUsersOneHundred() {
        AppUser u1 = AppUser.builder().id(UUID.randomUUID()).build();
        AppUser u2 = AppUser.builder().id(UUID.randomUUID()).build();
        AppUser u3 = AppUser.builder().id(UUID.randomUUID()).build();
        List<AppUser> participants = List.of(u1, u2, u3);

        Expense expense = Expense.builder().id(UUID.randomUUID()).amount(new BigDecimal("100.00")).build();

        List<ExpenseSplit> splits = sharedExpenseService.calculateEqualSplits(
                expense, participants, expense.getAmount()
        );

        assertEquals(3, splits.size());
        assertEquals(new BigDecimal("33.34"), splits.get(0).getShareAmount());
        assertEquals(new BigDecimal("33.33"), splits.get(1).getShareAmount());
        assertEquals(new BigDecimal("33.33"), splits.get(2).getShareAmount());

        BigDecimal sum = splits.stream()
                .map(ExpenseSplit::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), sum);
    }

    @Test
    @DisplayName("₹1000 split among 3 users must allocate ₹333.34, ₹333.33, ₹333.33 totaling exactly ₹1000.00")
    void testEqualSplitThreeUsersOneThousand() {
        AppUser u1 = AppUser.builder().id(UUID.randomUUID()).build();
        AppUser u2 = AppUser.builder().id(UUID.randomUUID()).build();
        AppUser u3 = AppUser.builder().id(UUID.randomUUID()).build();
        List<AppUser> participants = List.of(u1, u2, u3);

        Expense expense = Expense.builder().id(UUID.randomUUID()).amount(new BigDecimal("1000.00")).build();

        List<ExpenseSplit> splits = sharedExpenseService.calculateEqualSplits(
                expense, participants, expense.getAmount()
        );

        assertEquals(3, splits.size());
        assertEquals(new BigDecimal("333.34"), splits.get(0).getShareAmount());
        assertEquals(new BigDecimal("333.33"), splits.get(1).getShareAmount());
        assertEquals(new BigDecimal("333.33"), splits.get(2).getShareAmount());

        BigDecimal sum = splits.stream()
                .map(ExpenseSplit::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("1000.00"), sum);
    }

    @Test
    @DisplayName("₹100 split among 7 users totals exactly ₹100.00")
    void testEqualSplitSevenUsers() {
        List<AppUser> participants = List.of(
                AppUser.builder().id(UUID.randomUUID()).build(),
                AppUser.builder().id(UUID.randomUUID()).build(),
                AppUser.builder().id(UUID.randomUUID()).build(),
                AppUser.builder().id(UUID.randomUUID()).build(),
                AppUser.builder().id(UUID.randomUUID()).build(),
                AppUser.builder().id(UUID.randomUUID()).build(),
                AppUser.builder().id(UUID.randomUUID()).build()
        );

        Expense expense = Expense.builder().id(UUID.randomUUID()).amount(new BigDecimal("100.00")).build();

        List<ExpenseSplit> splits = sharedExpenseService.calculateEqualSplits(
                expense, participants, expense.getAmount()
        );

        assertEquals(7, splits.size());
        BigDecimal sum = splits.stream()
                .map(ExpenseSplit::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), sum);
    }
}
