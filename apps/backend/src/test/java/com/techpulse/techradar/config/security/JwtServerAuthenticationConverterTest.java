package com.techpulse.techradar.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServerAuthenticationConverterTest {

    private final JwtServerAuthenticationConverter converter = new JwtServerAuthenticationConverter();

    @Test
    void convert_extractsTokenAsPrincipalAndCredentials_whenBearerHeaderPresent() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/career").header("Authorization", "Bearer abc.def.ghi"));

        StepVerifier.create(converter.convert(exchange))
                .assertNext((Authentication auth) -> {
                    assertThat(auth.getPrincipal()).isEqualTo("abc.def.ghi");
                    assertThat(auth.getCredentials()).isEqualTo("abc.def.ghi");
                    assertThat(auth.isAuthenticated()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void convert_trimsWhitespaceAroundTheToken() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/career").header("Authorization", "Bearer   abc.def.ghi  "));

        StepVerifier.create(converter.convert(exchange))
                .assertNext((Authentication auth) -> assertThat(auth.getPrincipal()).isEqualTo("abc.def.ghi"))
                .verifyComplete();
    }

    @Test
    void convert_completesEmpty_whenNoAuthorizationHeader() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/career"));

        StepVerifier.create(converter.convert(exchange)).verifyComplete();
    }

    @Test
    void convert_completesEmpty_whenAuthorizationHeaderIsNotBearer() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/career").header("Authorization", "Basic dXNlcjpwYXNz"));

        StepVerifier.create(converter.convert(exchange)).verifyComplete();
    }

    @Test
    void convert_completesEmpty_whenBearerTokenIsEmpty() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/career").header("Authorization", "Bearer "));

        StepVerifier.create(converter.convert(exchange)).verifyComplete();
    }
}
