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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CorrelationIdGlobalFilterTest {

    private CorrelationIdGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdGlobalFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should generate new X-Request-Id when missing from incoming request")
    void shouldGenerateNewRequestIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v2/patients").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        String propagatedId = captured.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER);

        assertThat(propagatedId).isNotBlank();
        assertThat(UUID.fromString(propagatedId)).isNotNull();
    }

    @Test
    @DisplayName("Should preserve existing valid X-Request-Id from incoming request")
    void shouldPreserveExistingRequestId() {
        String existingId = "req-custom-12345";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v2/patients")
                .header(CorrelationIdGlobalFilter.REQUEST_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        String propagatedId = captured.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER);

        assertThat(propagatedId).isEqualTo(existingId);
    }
}
