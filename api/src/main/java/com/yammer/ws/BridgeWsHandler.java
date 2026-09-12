package com.yammer.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.service.BridgeService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Registry of live on-prem bridge WebSocket sessions, keyed by the {@code deviceId} each
 * bridge announces in its {@code HELLO} frame. The backend pushes print jobs ({@code RECEIPT})
 * over this channel and receives their outcome ({@code RECEIPT_RESULT}).
 *
 * <p>Several bridges may be connected (one phone per USB register). A new connection
 * supersedes only a previous session of the <em>same</em> device. Sessions receive no frames
 * until their HELLO registers them. {@link #sendTo} targets one device (USB registers);
 * {@link #sendToAnyReturningDevice} picks exactly one session for LAN (TCP) registers — never
 * a broadcast, which would print the same fiscal receipt on several bridges.
 */
@Component
@Slf4j
public class BridgeWsHandler extends TextWebSocketHandler {

    /** Registry key for bridges whose HELLO has no device id. */
    public static final String DEFAULT_DEVICE = "default";

    private final ObjectMapper mapper;
    private final BridgeService bridgeService;

    private final Set<WebSocketSession> pending = ConcurrentHashMap.newKeySet();
    private final Map<String, Registered> devices = new ConcurrentHashMap<>();

    private record Registered(WebSocketSession session, String deviceName, Instant connectedAt) {
    }

    /** A connected bridge device, as exposed to the backoffice device picker. */
    public record ConnectedDevice(String deviceId, String deviceName, Instant connectedAt) {
    }

    public BridgeWsHandler(ObjectMapper mapper, @Lazy BridgeService bridgeService) {
        this.mapper = mapper;
        this.bridgeService = bridgeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        pending.add(session);
        log.info("Bridge connection established — waiting for HELLO to register it.");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        pending.remove(session);
        devices.entrySet().removeIf(e -> {
            if (e.getValue().session() == session) {
                log.warn("Bridge device '{}' disconnected: {} ({} device(s) left).",
                        e.getKey(), status, devices.size() - 1);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = mapper.readTree(message.getPayload());
            String type = node.path("type").asText("");
            switch (type) {
                case "RECEIPT_RESULT" -> bridgeService.onResult(message.getPayload());
                case "HELLO" -> register(session, node);
                default -> log.debug("Ignoring bridge message of type '{}'", type);
            }
        } catch (Exception e) {
            log.error("Failed to handle bridge message: {}", e.getMessage(), e);
        }
    }

    private void register(WebSocketSession session, JsonNode hello) {
        String deviceId = hello.path("deviceId").asText("").trim();
        if (deviceId.isEmpty()) {
            deviceId = DEFAULT_DEVICE;
        }
        String deviceName = hello.path("deviceName").asText("").trim();
        pending.remove(session);
        Registered previous = devices.put(deviceId, new Registered(session, deviceName, Instant.now()));
        if (previous != null && previous.session() != session) {
            closeQuietly(previous.session());
        }
        log.info("Bridge device registered: '{}'{} ({} device(s) connected).",
                deviceId, deviceName.isEmpty() ? "" : " [" + deviceName + "]", devices.size());
        bridgeService.onDeviceRegistered(deviceId); // deliver frames that waited for this bridge
    }

    public boolean isConnected() {
        return devices.values().stream().anyMatch(r -> r.session().isOpen());
    }

    public boolean isDeviceConnected(String deviceId) {
        Registered r = deviceId == null ? null : devices.get(deviceId);
        return r != null && r.session().isOpen();
    }

    /** Currently connected devices, most recent first — feeds the backoffice device picker. */
    public List<ConnectedDevice> connectedDevices() {
        return devices.entrySet().stream()
                .filter(e -> e.getValue().session().isOpen())
                .sorted(Comparator.comparing((Map.Entry<String, Registered> e) ->
                        e.getValue().connectedAt()).reversed())
                .map(e -> new ConnectedDevice(e.getKey(), e.getValue().deviceName(), e.getValue().connectedAt()))
                .toList();
    }

    /** Push a frame to one device; true = the bytes entered its socket (not a delivery guarantee). */
    public boolean sendTo(String deviceId, String json) {
        Registered r = deviceId == null ? null : devices.get(deviceId);
        if (r == null || !r.session().isOpen()) {
            log.warn("Bridge device '{}' not connected — frame dropped.", deviceId);
            return false;
        }
        return write(r.session(), deviceId, json);
    }

    /** Push a frame to exactly ONE connected bridge (most recently registered); returns its device key. */
    public String sendToAnyReturningDevice(String json) {
        Registered def = devices.get(DEFAULT_DEVICE);
        if (def != null && def.session().isOpen()) {
            return write(def.session(), DEFAULT_DEVICE, json) ? DEFAULT_DEVICE : null;
        }
        return devices.entrySet().stream()
                .filter(e -> e.getValue().session().isOpen())
                .max(Comparator.comparing(e -> e.getValue().connectedAt()))
                .map(e -> write(e.getValue().session(), e.getKey(), json) ? e.getKey() : null)
                .orElseGet(() -> {
                    log.warn("No live bridge session — frame dropped.");
                    return null;
                });
    }

    private boolean write(WebSocketSession session, String deviceId, String json) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException e) {
            log.warn("Failed to push frame to bridge device '{}': {}", deviceId, e.getMessage());
            return false;
        }
    }

    /** Close sessions cleanly on shutdown so bridges reconnect fast. */
    @PreDestroy
    public void closeSessions() {
        pending.forEach(this::closeQuietly);
        pending.clear();
        devices.values().forEach(r -> closeQuietly(r.session()));
        devices.clear();
    }

    private void closeQuietly(WebSocketSession s) {
        try {
            if (s.isOpen()) {
                s.close(CloseStatus.GOING_AWAY);
            }
        } catch (IOException e) {
            log.debug("Error closing bridge session: {}", e.getMessage());
        }
    }
}
