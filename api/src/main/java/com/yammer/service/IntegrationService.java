package com.yammer.service;

import com.yammer.dto.IntegrationRequest;
import com.yammer.dto.IntegrationResponse;
import com.yammer.entity.ConnectionType;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.IntegrationType;
import com.yammer.repository.IntegrationRepository;
import com.yammer.security.AccessGuard;
import com.yammer.util.Strings;
import com.yammer.ws.BridgeWsHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Peripherals of a location. A MOBILE row is a bridge phone (device id from its HELLO);
 * a CASH_REGISTER / PRINTER is reached over TCP (ip) or through a MOBILE row it is
 * attached to (bridge_id) — the bridge then owns the USB link to the device.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final AccessGuard accessGuard;
    private final BridgeWsHandler bridgeWsHandler;

    @Transactional(readOnly = true)
    public List<IntegrationResponse> listByLocation(UUID locationId, IntegrationType type) {
        accessGuard.requireAccessibleLocation(locationId);
        List<IntegrationEntity> all = integrationRepository.findByLocationIdOrderByName(locationId);
        Map<UUID, String> bridgeNames = all.stream()
                .filter(i -> i.getType() == IntegrationType.MOBILE)
                .collect(Collectors.toMap(IntegrationEntity::getId, IntegrationEntity::getName));
        return all.stream()
                .filter(i -> type == null || i.getType() == type)
                .map(i -> toResponse(i, bridgeNames))
                .toList();
    }

    public IntegrationResponse create(IntegrationRequest request) {
        accessGuard.requireAccessibleLocation(request.locationId());
        IntegrationEntity entity = new IntegrationEntity();
        entity.setLocationId(request.locationId());
        apply(entity, request);
        return toResponse(integrationRepository.save(entity), null);
    }

    public IntegrationResponse update(UUID id, IntegrationRequest request) {
        IntegrationEntity entity = requireAccessibleIntegration(id);
        apply(entity, request);
        return toResponse(integrationRepository.save(entity), null);
    }

    public void delete(UUID id) {
        integrationRepository.delete(requireAccessibleIntegration(id));
    }

    // --- helpers ---

    private void apply(IntegrationEntity entity, IntegrationRequest request) {
        entity.setName(request.name().trim());
        entity.setType(request.type());
        if (request.type() == IntegrationType.MOBILE) {
            String deviceId = Strings.trimToNull(request.deviceId());
            if (deviceId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mobile needs its bridge device");
            }
            entity.setDeviceId(deviceId);
            entity.setConnection(ConnectionType.TCP); // not meaningful for a mobile; column is NOT NULL
            entity.setIp(null);
            entity.setBridgeId(null);
            return;
        }
        ConnectionType connection = request.connection() != null ? request.connection() : ConnectionType.TCP;
        entity.setConnection(connection);
        entity.setDeviceId(null);
        if (connection == ConnectionType.MOBILE) {
            IntegrationEntity bridge = request.bridgeId() == null ? null
                    : integrationRepository.findById(request.bridgeId()).orElse(null);
            if (bridge == null || bridge.getType() != IntegrationType.MOBILE
                    || !bridge.getLocationId().equals(entity.getLocationId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a mobile of this location");
            }
            entity.setBridgeId(bridge.getId());
            entity.setIp(null);
        } else {
            entity.setBridgeId(null);
            entity.setIp(Strings.trimToNull(request.ip()));
        }
    }

    private IntegrationResponse toResponse(IntegrationEntity entity, Map<UUID, String> bridgeNames) {
        String bridgeName = null;
        if (entity.getBridgeId() != null) {
            bridgeName = bridgeNames != null ? bridgeNames.get(entity.getBridgeId())
                    : integrationRepository.findById(entity.getBridgeId()).map(IntegrationEntity::getName).orElse(null);
        }
        Boolean online = entity.getType() == IntegrationType.MOBILE
                ? bridgeWsHandler.isDeviceConnected(entity.getDeviceId())
                : null;
        return IntegrationResponse.from(entity, bridgeName, online);
    }

    private IntegrationEntity requireAccessibleIntegration(UUID id) {
        IntegrationEntity entity = integrationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration not found: " + id));
        accessGuard.requireAccessibleLocation(entity.getLocationId());
        return entity;
    }
}
