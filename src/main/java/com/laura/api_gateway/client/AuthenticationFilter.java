package com.laura.api_gateway.client;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String AUTH_BASE_URL = "http://localhost:8081";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer ";
    public static final String AUTH_VALIDATE_TOKEN_URI = "/auth/validate-token";
    public static final String X_USER_EMAIL = "X-User-Email";
    private final WebClient webClient;

    public AuthenticationFilter(WebClient.Builder webClientBuilder){
        this.webClient = webClientBuilder.baseUrl(AUTH_BASE_URL).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        System.out.println("filtering...");
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String token = headers.getFirst(AUTHORIZATION);

        if(token == null || token.isEmpty()){
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String cleanToken = token.replace(BEARER, "");

        return webClient.get()
                .uri(AUTH_VALIDATE_TOKEN_URI)
                .header(HttpHeaders.AUTHORIZATION, cleanToken)
                .header(X_USER_EMAIL, headers.getFirst(X_USER_EMAIL))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {})
                .flatMap(response -> {
                    Boolean valid = response.get("valid");
                    if (Boolean.TRUE.equals(valid)) {
                        System.out.println("Token válido, pasando al micro destino.");
                        return chain.filter(exchange);
                    } else {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                })
                .onErrorResume(e -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
