package com.yammer.ws;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates the on-prem bridge's WebSocket handshake with a shared API key, supplied
 * as an {@code X-Bridge-Key} header or a {@code ?key=} query param. The bridge is a machine,
 * not a user, so it uses an API key rather than the JWT of {@link WsAuthHandshakeInterceptor}.
 *
 * <p>A blank {@code bridge.api-key} is accepted only under the {@code local}/{@code dev}
 * profile; anywhere else it is a configuration error and the handshake is rejected
 * (fail-closed) so an unconfigured server never accepts anonymous bridges.
 */
@Component
@Slf4j
public class BridgeAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final String HEADER_API_KEY = "X-Bridge-Key";

    @Value("${bridge.api-key:}")
    private String apiKey;

    private final AtomicBoolean blankKeyWarned = new AtomicBoolean(false);
    private final Environment environment;

    public BridgeAuthHandshakeInterceptor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (apiKey == null || apiKey.isBlank()) {
            if (isDevProfile()) {
                if (blankKeyWarned.compareAndSet(false, true)) {
                    log.warn("bridge.api-key is blank — accepting bridge handshakes without auth (dev profile only).");
                }
                return true;
            }
            log.error("bridge.api-key is blank in a non-dev profile — rejecting bridge handshake. Set BRIDGE_API_KEY.");
            return false;
        }
        String provided = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("key");
        if (provided == null) {
            provided = request.getHeaders().getFirst(HEADER_API_KEY);
        }
        if (constantTimeEquals(apiKey, provided)) {
            return true;
        }
        log.warn("Rejected bridge handshake: invalid or missing API key.");
        return false;
    }

    private boolean isDevProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("dev"));
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
