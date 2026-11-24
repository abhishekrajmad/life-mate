package com.lifemate.expense.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemate.expense.entity.ExpenseOutboxEvent;
import com.lifemate.expense.repository.ExpenseOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseOutboxPublisherTest {
    @Mock ExpenseOutboxRepository repository;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks ExpenseOutboxPublisher publisher;

    private ExpenseOutboxEvent pending;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "objectMapper", new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(publisher, "exchangeName", "expense.exchange");
        ReflectionTestUtils.setField(publisher, "routingKey", "expense.created");
        ReflectionTestUtils.setField(publisher, "confirmTimeoutMs", 5000L);
        pending = new ExpenseOutboxEvent("event-1", "{\"eventId\":\"event-1\"}");
        when(repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAscEventIdAsc())
                .thenReturn(List.of(pending));
    }

    @Test
    void brokerFailureLeavesEventPendingForRetry() {
        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpException("broker unavailable"));

        publisher.publishPending();

        assertNull(pending.getPublishedAt());
        verify(repository, never()).save(any());
    }

    @Test
    void confirmedPublishMarksEventSent() {
        when(rabbitTemplate.invoke(any())).thenReturn(null);

        publisher.publishPending();

        assertNotNull(pending.getPublishedAt());
        verify(repository).save(pending);
    }
}
