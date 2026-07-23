package com.techpulse.techradar.shared.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpUtilsTest {

    @Test
    void usesXRealIp_whenPresent() {
        var request = MockServerHttpRequest.get("/auth/login")
                .header("X-Real-IP", "203.0.113.7")
                .build();

        assertThat(ClientIpUtils.resolveClientIp(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void ignoresClientSuppliedXForwardedFor_evenWithoutXRealIp() {
        // nginx appends the real address AFTER whatever the client sent in X-Forwarded-For rather
        // than replacing it, so trusting this header (or its first entry) lets a client spoof its
        // own IP. Confirm the resolver falls back to the socket address instead of reading it.
        var request = MockServerHttpRequest.get("/auth/login")
                .header("X-Forwarded-For", "9.9.9.9, 203.0.113.7")
                .remoteAddress(new InetSocketAddress("198.51.100.1", 54321))
                .build();

        assertThat(ClientIpUtils.resolveClientIp(request)).isEqualTo("198.51.100.1");
    }

    @Test
    void fallsBackToRemoteAddress_whenNoXRealIpHeader() {
        var request = MockServerHttpRequest.get("/auth/login")
                .remoteAddress(new InetSocketAddress("198.51.100.1", 54321))
                .build();

        assertThat(ClientIpUtils.resolveClientIp(request)).isEqualTo("198.51.100.1");
    }

    @Test
    void returnsUnknown_whenNeitherHeaderNorRemoteAddressAvailable() {
        var request = MockServerHttpRequest.get("/auth/login").build();

        assertThat(ClientIpUtils.resolveClientIp(request)).isEqualTo("unknown");
    }
}
