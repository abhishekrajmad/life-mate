package com.lifemate.expense.repository;

import com.lifemate.expense.entity.ExpenseOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseOutboxRepository extends JpaRepository<ExpenseOutboxEvent, String> {
    List<ExpenseOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAscEventIdAsc();
}
