package com.lifemate.gateway;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PublicExposureSecurityValidator {

    private static final int MINIMUM_TOKEN_LENGTH = 32;
    private static final Set<String> LOOPBACK_ADDRESSES = Set.of(
            "localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final GatewaySecurityProperties security;
    private final String serverAddress;

    public PublicExposureSecurityValidator(
            GatewaySecurityProperties security,
            @Value("${server.address:127.0.0.1}") String serverAddress) {
        this.security = security;
        this.serverAddress = serverAddress;
    }

    @PostConstruct
    void validate() {
        requireUserId("GATEWAY_API_USER_ID", security.getApiUserId());
        requireUserId("GATEWAY_WEBHOOK_USER_ID", security.getWebhookUserId());
        if (!isLoopback(serverAddress) && !security.isEnabled()) {
            throw new IllegalStateException(
                    "Gateway authentication must be enabled before binding to a non-loopback address");
        }
        if (security.isEnabled()) {
            requireStrongToken("GATEWAY_API_TOKEN", security.getApiToken());
            requireStrongToken("GATEWAY_WEBHOOK_TOKEN", security.getWebhookToken());
            if (security.getApiToken().equals(security.getWebhookToken())) {
                throw new IllegalStateException("API and webhook tokens must be different");
            }
        }
    }

    private boolean isLoopback(String address) {
        String normalized = address == null ? "" : address.trim().toLowerCase();
        return LOOPBACK_ADDRESSES.contains(normalized) || normalized.startsWith("127.");
    }

    private void requireStrongToken(String variableName, String token) {
        if (token == null || token.isBlank() || token.length() < MINIMUM_TOKEN_LENGTH) {
            throw new IllegalStateException(variableName + " must contain at least 32 characters");
        }
    }

    private void requireUserId(String variableName, String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 50) {
            throw new IllegalStateException(variableName + " must contain between 1 and 50 characters");
        }
    }
}
