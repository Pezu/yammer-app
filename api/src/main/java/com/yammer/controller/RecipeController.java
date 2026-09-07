package com.yammer.controller;

import com.yammer.dto.RecipeComponentRequest;
import com.yammer.dto.RecipeComponentResponse;
import com.yammer.service.RecipeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER')") // recipe editing is admin-only; read relaxed below
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    public List<RecipeComponentResponse> get(@PathVariable UUID productId) {
        return recipeService.get(productId);
    }

    /** Replace the product's recipe with the posted rows. */
    @PutMapping("/{productId}")
    public List<RecipeComponentResponse> save(
            @PathVariable UUID productId, @Valid @RequestBody List<RecipeComponentRequest> rows) {
        return recipeService.save(productId, rows);
    }
}
