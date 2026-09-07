package com.yammer.service;

import com.yammer.dto.RecipeComponentRequest;
import com.yammer.dto.RecipeComponentResponse;
import com.yammer.entity.ProductEntity;
import com.yammer.entity.RecipeComponentEntity;
import com.yammer.repository.ProductRepository;
import com.yammer.repository.RecipeComponentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class RecipeService {

    private final RecipeComponentRepository recipeComponentRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    /** The product's recipe rows, component names resolved. */
    @Transactional(readOnly = true)
    public List<RecipeComponentResponse> get(UUID productId) {
        productService.requireAccessibleProduct(productId);
        List<RecipeComponentEntity> rows = recipeComponentRepository.findByProductIdOrderBySortOrder(productId);
        Map<UUID, String> names = productRepository
                .findAllById(rows.stream().map(RecipeComponentEntity::getComponentProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, ProductEntity::getName));
        return rows.stream()
                .map(r -> new RecipeComponentResponse(
                        r.getId(),
                        r.getComponentProductId(),
                        names.getOrDefault(r.getComponentProductId(), "?"),
                        r.getQuantity()))
                .toList();
    }

    /** Replaces the product's recipe with the given rows (order preserved). */
    public List<RecipeComponentResponse> save(UUID productId, List<RecipeComponentRequest> rows) {
        ProductEntity product = productService.requireAccessibleProduct(productId);
        List<RecipeComponentEntity> toSave = new ArrayList<>();
        int order = 0;
        for (RecipeComponentRequest row : rows) {
            if (Objects.equals(row.componentProductId(), productId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product cannot be its own component");
            }
            ProductEntity component = productRepository.findById(row.componentProductId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Unknown component product: " + row.componentProductId()));
            if (!Objects.equals(component.getLocationId(), product.getLocationId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Component is not in the product's location");
            }
            RecipeComponentEntity entity = new RecipeComponentEntity();
            entity.setProductId(productId);
            entity.setComponentProductId(row.componentProductId());
            entity.setQuantity(row.quantity());
            entity.setSortOrder(order++);
            toSave.add(entity);
        }
        recipeComponentRepository.deleteByProductId(productId);
        recipeComponentRepository.saveAll(toSave);
        return get(productId);
    }
}
