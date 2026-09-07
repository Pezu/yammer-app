package com.yammer.service;

import com.yammer.dto.SelfPayTypeRequest;
import com.yammer.dto.SelfPayTypeResponse;
import com.yammer.entity.SelfPayTypeEntity;
import com.yammer.repository.SelfPayTypeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SelfPayTypeService {

    private final SelfPayTypeRepository selfPayTypeRepository;

    public List<SelfPayTypeResponse> list() {
        return selfPayTypeRepository.findAll(Sort.by("type")).stream()
                .map(SelfPayTypeResponse::from)
                .toList();
    }

    public SelfPayTypeResponse create(SelfPayTypeRequest request) {
        String name = request.type().trim();
        if (selfPayTypeRepository.existsByTypeIgnoreCase(name)) {
            throw conflict(name);
        }
        SelfPayTypeEntity entity = new SelfPayTypeEntity();
        entity.setType(name);
        return SelfPayTypeResponse.from(selfPayTypeRepository.save(entity));
    }

    public SelfPayTypeResponse update(UUID id, SelfPayTypeRequest request) {
        SelfPayTypeEntity entity = selfPayTypeRepository.findById(id).orElseThrow(() -> notFound(id));
        String name = request.type().trim();
        if (!name.equalsIgnoreCase(entity.getType()) && selfPayTypeRepository.existsByTypeIgnoreCase(name)) {
            throw conflict(name);
        }
        entity.setType(name);
        return SelfPayTypeResponse.from(selfPayTypeRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!selfPayTypeRepository.existsById(id)) {
            throw notFound(id);
        }
        selfPayTypeRepository.deleteById(id);
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Self pay type not found: " + id);
    }

    private ResponseStatusException conflict(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Self pay type already exists: " + name);
    }
}
