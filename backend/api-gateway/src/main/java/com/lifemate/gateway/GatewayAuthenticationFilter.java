package com.lifemate.gateway;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayAuthenticationFilter implements WebFilter {

    static final String WEBHOOK_PATH = "/api/v1/expenses/webhook/sms";
    static final String WEBHOOK_TOKEN_HEADER = "X-Webhook-Token";

    private static final byte[] UNAUTHORIZED_BODY =
            "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);

    private final GatewaySecurityProperties security;

    public GatewayAuthenticationFilter(GatewaySecurityProperties security) {
        this.security = security;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod()) || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        boolean webhookRequest = WEBHOOK_PATH.equals(path);
        String suppliedToken = webhookRequest
                ? exchange.getRequest().getHeaders().getFirst(WEBHOOK_TOKEN_HEADER)
                : bearerToken(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        String expectedToken = webhookRequest ? security.getWebhookToken() : security.getApiToken();

        if (security.isEnabled() && !tokensMatch(suppliedToken, expectedToken)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(UNAUTHORIZED_BODY)));
        }

        String authenticatedUser = webhookRequest
                ? security.getWebhookUserId() : security.getApiUserId();

        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove(WEBHOOK_TOKEN_HEADER);
                    headers.remove(GatewayHeaders.AUTHENTICATED_USER);
                    headers.set(GatewayHeaders.AUTHENTICATED_USER, authenticatedUser);
                }))
                .build();
        return chain.filter(sanitizedExchange);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    private boolean tokensMatch(String supplied, String expected) {
        if (supplied == null || expected == null || supplied.isBlank() || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
