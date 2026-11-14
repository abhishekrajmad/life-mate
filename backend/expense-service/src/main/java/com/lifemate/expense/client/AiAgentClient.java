package com.lifemate.expense.client;

import com.lifemate.expense.dto.AiParseResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class AiAgentClient {

    private final RestClient restClient;

    public AiAgentClient(@Value("${ai-agent.base-url}") String aiAgentBaseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5 seconds
        factory.setReadTimeout(35000);    // 35 seconds

        this.restClient = RestClient.builder()
                .baseUrl(aiAgentBaseUrl)
                .requestFactory(factory)
                .build();
    }

    public AiParseResponseDTO parseExpense(String rawText, String currency) {
        log.info("Calling ai-agent-service to parse text: {}", rawText);

        Map<String, String> requestBody = Map.of(
                "raw_text", rawText,
                "currency", currency != null ? currency : "INR"
        );

        AiParseResponseDTO response = restClient.post()
                .uri("/api/v1/ai/parse-expense")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(AiParseResponseDTO.class);

        if (response == null || response.getAmount() == null) {
            throw new IllegalStateException("AI Agent returned an empty or invalid response");
        }

        log.info("AI parsed expense -> Amount: {}, Category: {}, Merchant: {}",
                response.getAmount(), response.getCategory(), response.getMerchant());
        return response;
    }
}