package com.lifemate.expense.repository;

import com.lifemate.expense.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findAllByUserIdOrderByAccountNameAsc(String userId);

    Optional<Account> findByIdAndUserId(Long id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.userId = :userId")
    Optional<Account> findByIdAndUserIdForUpdate(
            @Param("id") Long id, @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a " +
            "WHERE a.userId = :userId AND a.id IN :ids ORDER BY a.id")
    List<Account> findAllByUserIdAndIdInForUpdate(
            @Param("userId") String userId, @Param("ids") Collection<Long> ids);
}
