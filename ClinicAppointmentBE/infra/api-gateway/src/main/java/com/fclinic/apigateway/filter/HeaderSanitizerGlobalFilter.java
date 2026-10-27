package com.fclinic.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Strips spoofed/internal identity headers arriving from external clients.
 * Downstream services must only rely on genuine Bearer token introspection
 * or internal machine credentials, not untrusted HTTP headers.
 */
@Component
public class HeaderSanitizerGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(HeaderSanitizerGlobalFilter.class);

    private static final List<String> FORBIDDEN_INBOUND_HEADERS = List.of(
            "X-User-Id",
            "X-User-Role",
            "X-User-Roles",
            "X-Service-Name",
            "X-Internal-Service",
            "X-Doctor-Id",
            "X-Patient-Id"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        boolean hasForbiddenHeader = FORBIDDEN_INBOUND_HEADERS.stream()
                .anyMatch(h -> request.getHeaders().containsHeader(h));

        if (hasForbiddenHeader) {
            log.warn("[HeaderSanitizer] Stripping spoofed internal identity headers from path={}", request.getPath());
            ServerHttpRequest.Builder builder = request.mutate();
            for (String header : FORBIDDEN_INBOUND_HEADERS) {
                if (request.getHeaders().containsHeader(header)) {
                    builder.headers(httpHeaders -> httpHeaders.remove(header));
                }
            }
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
