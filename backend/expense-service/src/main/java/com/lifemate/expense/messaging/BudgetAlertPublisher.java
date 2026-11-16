package com.lifemate.expense.messaging;

import com.lifemate.expense.dto.BudgetExceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetAlertPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.budget-exceeded}")
    private String routingKey;

    public void publishBudgetExceeded(BudgetExceededEvent event) {
        log.warn("BUDGET BREACH DETECTED -> Category: '{}', Spent: {}, Limit: {} — Publishing event to RabbitMQ",
                event.getCategoryName(), event.getCurrentSpent(), event.getMonthlyLimit());

        rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
    }
}