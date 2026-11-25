package com.lifemate.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthenticationFilterTest {

    private static final String API_TOKEN = "api-token-with-at-least-32-characters";
    private static final String WEBHOOK_TOKEN = "webhook-token-with-at-least-32-characters";

    private GatewaySecurityProperties security;
    private GatewayAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        security = new GatewaySecurityProperties();
        security.setEnabled(true);
        security.setApiToken(API_TOKEN);
        security.setWebhookToken(WEBHOOK_TOKEN);
        security.setApiUserId("api-user");
        security.setWebhookUserId("webhook-user");
        filter = new GatewayAuthenticationFilter(security);
    }

    @Test
    void acceptsBearerTokenAndRemovesItBeforeForwarding() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN)
                        .header(GatewayHeaders.AUTHENTICATED_USER, "spoofed-user"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            assertThat(forwardedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
            assertThat(forwardedExchange.getRequest().getHeaders()
                    .getFirst(GatewayHeaders.AUTHENTICATED_USER)).isEqualTo("api-user");
            return forwardedExchange.getResponse().setComplete();
        }).block();

        assertThat(forwarded).isTrue();
    }

    @Test
    void rejectsMissingBearerToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/dashboard/summary"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            return forwardedExchange.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isFalse();
    }

    @Test
    void webhookRequiresItsDedicatedToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(GatewayAuthenticationFilter.WEBHOOK_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN));

        filter.filter(exchange, forwardedExchange -> forwardedExchange.getResponse().setComplete()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void acceptsWebhookTokenAndRemovesItBeforeForwarding() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(GatewayAuthenticationFilter.WEBHOOK_PATH)
                        .header(GatewayAuthenticationFilter.WEBHOOK_TOKEN_HEADER, WEBHOOK_TOKEN));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            assertThat(forwardedExchange.getRequest().getHeaders()
                    .getFirst(GatewayAuthenticationFilter.WEBHOOK_TOKEN_HEADER)).isNull();
            assertThat(forwardedExchange.getRequest().getHeaders()
                    .getFirst(GatewayHeaders.AUTHENTICATED_USER)).isEqualTo("webhook-user");
            return forwardedExchange.getResponse().setComplete();
        }).block();

        assertThat(forwarded).isTrue();
    }

    @Test
    void allowsCorsPreflightWithoutCredentials() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/expenses"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            assertThat(forwardedExchange.getRequest().getHeaders()
                    .getFirst(GatewayHeaders.AUTHENTICATED_USER)).isNull();
            return forwardedExchange.getResponse().setComplete();
        }).block();

        assertThat(forwarded).isTrue();
    }

    @Test
    void disabledAuthenticationPreservesLoopbackDevelopment() {
        security.setEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/expenses"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            assertThat(forwardedExchange.getRequest().getHeaders()
                    .getFirst(GatewayHeaders.AUTHENTICATED_USER)).isEqualTo("api-user");
            return forwardedExchange.getResponse().setComplete();
        }).block();

        assertThat(forwarded).isTrue();
    }
}
