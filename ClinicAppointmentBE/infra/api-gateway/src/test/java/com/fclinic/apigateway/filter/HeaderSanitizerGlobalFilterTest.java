package com.fclinic.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HeaderSanitizerGlobalFilterTest {

    private HeaderSanitizerGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new HeaderSanitizerGlobalFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should strip spoofed internal identity headers from inbound request")
    void shouldStripSpoofedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v2/patients")
                .header("Authorization", "Bearer valid.token.jwt")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "ADMIN")
                .header("X-Doctor-Id", "99")
                .header("X-Service-Name", "appointment-service")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        assertThat(captured.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer valid.token.jwt");
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Role")).isNull();
        assertThat(captured.getRequest().getHeaders().getFirst("X-Doctor-Id")).isNull();
        assertThat(captured.getRequest().getHeaders().getFirst("X-Service-Name")).isNull();
    }

    @Test
    @DisplayName("Should pass request unchanged when no forbidden headers are present")
    void shouldPassCleanRequestUnchanged() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v2/patients")
                .header("Authorization", "Bearer valid.token.jwt")
                .header("X-Request-Id", "req-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }
}
