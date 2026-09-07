package com.yammer.service;

import com.yammer.dto.OrderPointTypeRequest;
import com.yammer.dto.OrderPointTypeResponse;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.repository.OrderPointTypeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrderPointTypeService {

    private final OrderPointTypeRepository orderPointTypeRepository;

    public List<OrderPointTypeResponse> list() {
        return orderPointTypeRepository.findAll(Sort.by("type")).stream()
                .map(OrderPointTypeResponse::from)
                .toList();
    }

    public OrderPointTypeResponse create(OrderPointTypeRequest request) {
        String name = request.type().trim();
        if (orderPointTypeRepository.existsByTypeIgnoreCase(name)) {
            throw conflict(name);
        }
        OrderPointTypeEntity entity = new OrderPointTypeEntity();
        entity.setType(name);
        return OrderPointTypeResponse.from(orderPointTypeRepository.save(entity));
    }

    public OrderPointTypeResponse update(UUID id, OrderPointTypeRequest request) {
        OrderPointTypeEntity entity = orderPointTypeRepository.findById(id).orElseThrow(() -> notFound(id));
        String name = request.type().trim();
        if (!name.equalsIgnoreCase(entity.getType()) && orderPointTypeRepository.existsByTypeIgnoreCase(name)) {
            throw conflict(name);
        }
        entity.setType(name);
        return OrderPointTypeResponse.from(orderPointTypeRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!orderPointTypeRepository.existsById(id)) {
            throw notFound(id);
        }
        orderPointTypeRepository.deleteById(id);
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point type not found: " + id);
    }

    private ResponseStatusException conflict(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Order point type already exists: " + name);
    }
}
