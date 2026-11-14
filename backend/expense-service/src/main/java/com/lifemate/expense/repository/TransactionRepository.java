package com.lifemate.expense.repository;

import com.lifemate.expense.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.category.id = :categoryId " +
           "AND t.transactionType = 'EXPENSE' " +
           "AND t.createdAt >= :startOfMonth")
    BigDecimal sumMonthlyExpenseByCategory(
            @Param("categoryId") Long categoryId,
            @Param("startOfMonth") LocalDateTime startOfMonth
    );
}