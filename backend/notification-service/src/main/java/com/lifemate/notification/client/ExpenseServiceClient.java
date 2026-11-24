package com.lifemate.notification.client;

import com.lifemate.notification.dto.CategoryBudgetStatusDTO;
import com.lifemate.notification.dto.BudgetAlertReceiptResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class ExpenseServiceClient {
    private final RestClient restClient;

    public ExpenseServiceClient(@Value("${expense-service.base-url}") String baseUrl,
                                @Value("${expense-service.connect-timeout-ms}") int connectTimeoutMs,
                                @Value("${expense-service.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public CategoryBudgetStatusDTO getBudgetStatus(Long categoryId, YearMonth month, Long throughTransactionId) {
        CategoryBudgetStatusDTO status = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/categories/{categoryId}/budget-status")
                        .queryParam("month", month.toString())
                        .queryParam("throughTransactionId", throughTransactionId).build(categoryId))
                .retrieve().body(CategoryBudgetStatusDTO.class);
        if (status == null) {
            throw new IllegalStateException("Expense service returned no budget status");
        }
        return status;
    }

    public boolean recordBudgetAlert(String eventId, Long categoryId, BigDecimal limit, BigDecimal spent) {
        BudgetAlertReceiptResponseDTO response = restClient.post()
                .uri("/api/v1/budget-alerts")
                .body(Map.of("eventId", eventId, "categoryId", categoryId,
                        "monthlyLimit", limit, "spent", spent))
                .retrieve().body(BudgetAlertReceiptResponseDTO.class);
        if (response == null) {
            throw new IllegalStateException("Expense service returned no alert receipt");
        }
        return response.created();
    }
}
