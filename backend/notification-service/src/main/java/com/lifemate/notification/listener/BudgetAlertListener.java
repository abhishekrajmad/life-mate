package com.lifemate.notification.listener;

import com.lifemate.notification.dto.BudgetExceededEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BudgetAlertListener {

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void handleBudgetExceeded(BudgetExceededEvent event) {
        log.warn("==========================================================");
        log.warn("🚨 LIFE MATE BUDGET ALERT 🚨");
        log.warn("Category     : {}", event.getCategoryName());
        log.warn("Monthly Limit: ₹{}", event.getMonthlyLimit());
        log.warn("Total Spent  : ₹{} (Over by ₹{})", event.getCurrentSpent(), event.getExceededBy());
        log.warn("Triggered By : ₹{} at {}", event.getTriggeringAmount(), event.getTriggeringMerchant());
        log.warn("==========================================================");
    }
}