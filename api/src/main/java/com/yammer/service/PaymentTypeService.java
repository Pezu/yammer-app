package com.yammer.service;

import com.yammer.dto.PaymentTypeRequest;
import com.yammer.dto.PaymentTypeResponse;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.repository.PaymentTypeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentTypeService {

    private final PaymentTypeRepository paymentTypeRepository;

    public List<PaymentTypeResponse> list() {
        return paymentTypeRepository.findAll(Sort.by("type")).stream()
                .map(PaymentTypeResponse::from)
                .toList();
    }

    public PaymentTypeResponse create(PaymentTypeRequest request) {
        String name = request.type().trim();
        if (paymentTypeRepository.existsByTypeIgnoreCase(name)) {
            throw conflict(name);
        }
        PaymentTypeEntity entity = new PaymentTypeEntity();
        entity.setType(name);
        return PaymentTypeResponse.from(paymentTypeRepository.save(entity));
    }

    public PaymentTypeResponse update(UUID id, PaymentTypeRequest request) {
        PaymentTypeEntity entity = paymentTypeRepository.findById(id).orElseThrow(() -> notFound(id));
        String name = request.type().trim();
        if (!name.equalsIgnoreCase(entity.getType()) && paymentTypeRepository.existsByTypeIgnoreCase(name)) {
            throw conflict(name);
        }
        entity.setType(name);
        return PaymentTypeResponse.from(paymentTypeRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!paymentTypeRepository.existsById(id)) {
            throw notFound(id);
        }
        paymentTypeRepository.deleteById(id);
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment type not found: " + id);
    }

    private ResponseStatusException conflict(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Payment type already exists: " + name);
    }
}
