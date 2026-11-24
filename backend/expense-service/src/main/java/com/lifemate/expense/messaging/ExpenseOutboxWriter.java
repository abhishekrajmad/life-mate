package com.lifemate.expense.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemate.expense.dto.ExpenseCreatedEvent;
import com.lifemate.expense.entity.ExpenseOutboxEvent;
import com.lifemate.expense.repository.ExpenseOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpenseOutboxWriter {
    private final ExpenseOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public void save(ExpenseCreatedEvent event) {
        try {
            repository.save(new ExpenseOutboxEvent(event.getEventId(), objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize expense event", ex);
        }
    }
}
