package com.techpulse.techradar.shared.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Resolves the originating client IP for rate limiting.
 * <p>
 * Trusts {@code X-Real-IP} over the socket address, which behind the nginx reverse proxy
 * (apps/web/nginx.conf) is always the proxy's own container IP. Deliberately does NOT read
 * {@code X-Forwarded-For}: nginx sets it via {@code $proxy_add_x_forwarded_for}, which APPENDS
 * the real address after whatever value the client already sent rather than replacing it —
 * so the first entry in that header is attacker-controlled and trivially spoofable. X-Real-IP,
 * by contrast, is set with {@code proxy_set_header X-Real-IP $remote_addr}, which always
 * overwrites (never appends), so nothing the client sends can influence it.
 */
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String resolveClientIp(ServerHttpRequest request) {
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
