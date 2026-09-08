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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Peripherals of a location: cash registers and printers, reached over TCP (ip) or through
 * a bridge phone they are attached to (connection MOBILE + the phone's device id). The
 * phone is pure routing — the backend forwards the job to it and it drives the device.
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
        List<IntegrationEntity> rows = type != null
                ? integrationRepository.findByLocationIdAndTypeOrderByName(locationId, type)
                : integrationRepository.findByLocationIdOrderByName(locationId);
        return rows.stream().map(this::toResponse).toList();
    }

    public IntegrationResponse create(IntegrationRequest request) {
        accessGuard.requireAccessibleLocation(request.locationId());
        IntegrationEntity entity = new IntegrationEntity();
        entity.setLocationId(request.locationId());
        apply(entity, request);
        return toResponse(integrationRepository.save(entity));
    }

    public IntegrationResponse update(UUID id, IntegrationRequest request) {
        IntegrationEntity entity = requireAccessibleIntegration(id);
        apply(entity, request);
        return toResponse(integrationRepository.save(entity));
    }

    public void delete(UUID id) {
        integrationRepository.delete(requireAccessibleIntegration(id));
    }

    // --- helpers ---

    private void apply(IntegrationEntity entity, IntegrationRequest request) {
        entity.setName(request.name().trim());
        entity.setType(request.type());
        ConnectionType connection = request.connection() != null ? request.connection() : ConnectionType.TCP;
        entity.setConnection(connection);
        if (connection == ConnectionType.MOBILE) {
            String deviceId = Strings.trimToNull(request.deviceId());
            if (deviceId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick the phone this device is attached to");
            }
            entity.setDeviceId(deviceId);
            entity.setDeviceName(Strings.trimToNull(request.deviceName()));
            entity.setIp(null);
        } else {
            entity.setDeviceId(null);
            entity.setDeviceName(null);
            entity.setIp(Strings.trimToNull(request.ip()));
        }
    }

    private IntegrationResponse toResponse(IntegrationEntity entity) {
        Boolean online = entity.getConnection() == ConnectionType.MOBILE
                ? bridgeWsHandler.isDeviceConnected(entity.getDeviceId())
                : null;
        return IntegrationResponse.from(entity, online);
    }

    private IntegrationEntity requireAccessibleIntegration(UUID id) {
        IntegrationEntity entity = integrationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration not found: " + id));
        accessGuard.requireAccessibleLocation(entity.getLocationId());
        return entity;
    }
}
