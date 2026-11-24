package com.lifemate.expense.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemate.expense.dto.ExpenseCreatedEvent;
import com.lifemate.expense.entity.ExpenseOutboxEvent;
import com.lifemate.expense.repository.ExpenseOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseOutboxPublisher {
    private final ExpenseOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.expense-created}")
    private String routingKey;

    @Value("${outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (ExpenseOutboxEvent pending : repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAscEventIdAsc()) {
            try {
                ExpenseCreatedEvent event = objectMapper.readValue(pending.getPayload(), ExpenseCreatedEvent.class);
                rabbitTemplate.invoke(operations -> {
                    operations.convertAndSend(exchangeName, routingKey, event);
                    operations.waitForConfirmsOrDie(confirmTimeoutMs);
                    return null;
                });
                pending.setPublishedAt(LocalDateTime.now());
                repository.save(pending);
                log.info("Published expense event {}", pending.getEventId());
            } catch (JsonProcessingException ex) {
                log.error("Invalid outbox payload for event {}", pending.getEventId(), ex);
                break;
            } catch (Exception ex) {
                log.warn("Could not publish expense event {}; retrying later", pending.getEventId(), ex);
                break;
            }
        }
    }
}
