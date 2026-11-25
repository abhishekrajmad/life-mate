package com.lifemate.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
public class ClientKeyResolverConfig {
    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress == null || remoteAddress.getAddress() == null) {
                return reactor.core.publisher.Mono.just("unknown");
            }
            return reactor.core.publisher.Mono.just(remoteAddress.getAddress().getHostAddress());
        };
    }
}
