package com.lifemate.expense.repository;

import com.lifemate.expense.entity.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findAllByAccount_UserIdOrderByCreatedAtDesc(String userId);

    Optional<Transaction> findByIdAndAccount_UserId(Long id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.account.userId = :userId")
    Optional<Transaction> findByIdAndAccountUserIdForUpdate(
            @Param("id") Long id, @Param("userId") String userId);

    boolean existsByAccount_Id(Long accountId);

    boolean existsByCategory_Id(Long categoryId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.category.id = :categoryId " +
           "AND t.transactionType = 'EXPENSE' " +
           "AND t.account.userId = :userId " +
           "AND t.createdAt >= :startOfMonth")
    BigDecimal sumMonthlyExpensesByCategory(
            @Param("categoryId") Long categoryId,
            @Param("userId") String userId,
            @Param("startOfMonth") LocalDateTime startOfMonth
    );

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.category.id = :categoryId AND t.account.userId = :userId " +
           "AND t.transactionType = 'EXPENSE' " +
           "AND t.createdAt >= :start AND t.createdAt < :end AND t.id <= :transactionId")
    BigDecimal sumExpensesThroughTransaction(@Param("categoryId") Long categoryId,
                                              @Param("userId") String userId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("transactionId") Long transactionId);
}
