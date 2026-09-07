package com.yammer.service;

import com.yammer.dto.ProductRequest;
import com.yammer.dto.ProductResponse;
import com.yammer.entity.ProductEntity;
import com.yammer.repository.ProductRepository;
import com.yammer.repository.VatTypeRepository;
import com.yammer.security.AccessGuard;
import com.yammer.util.Strings;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final VatTypeRepository vatTypeRepository;
    private final AccessGuard accessGuard;

    /** The location's catalog, last-introduced first. */
    @Transactional(readOnly = true)
    public List<ProductResponse> list(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        return productRepository.findByLocationIdOrderByCreatedAtDesc(locationId).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse create(ProductRequest request) {
        accessGuard.requireAccessibleLocation(request.locationId());
        ProductEntity entity = new ProductEntity();
        entity.setLocationId(request.locationId());
        apply(entity, request);
        return ProductResponse.from(productRepository.save(entity));
    }

    public ProductResponse update(UUID id, ProductRequest request) {
        ProductEntity entity = requireAccessibleProduct(id);
        apply(entity, request); // locationId is not moved — the product stays in its location
        return ProductResponse.from(productRepository.save(entity));
    }

    public void delete(UUID id) {
        ProductEntity entity = requireAccessibleProduct(id);
        // menu items and recipe components referencing it cascade away (ON DELETE CASCADE)
        productRepository.delete(entity);
    }

    /** The product, tenant-checked through its location (404 across tenants). */
    public ProductEntity requireAccessibleProduct(UUID id) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
        accessGuard.requireAccessibleLocation(entity.getLocationId());
        return entity;
    }

    private void apply(ProductEntity entity, ProductRequest request) {
        entity.setName(request.name().trim());
        entity.setDescription(Strings.trimToNull(request.description()));
        if (request.vatTypeId() != null && !vatTypeRepository.existsById(request.vatTypeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown VAT type: " + request.vatTypeId());
        }
        entity.setVatTypeId(request.vatTypeId());
        entity.setImageObject(Strings.trimToNull(request.imageObject()));
    }
}
