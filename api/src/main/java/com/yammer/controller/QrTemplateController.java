package com.yammer.controller;

import com.yammer.dto.QrTemplateRequest;
import com.yammer.dto.QrTemplateResponse;
import com.yammer.service.QrTemplateService;
import com.yammer.service.StorageService.StoredObject;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/qr-templates")
@RequiredArgsConstructor
public class QrTemplateController {

    private final QrTemplateService qrTemplateService;

    @GetMapping
    public List<QrTemplateResponse> list() {
        return qrTemplateService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER')")
    public QrTemplateResponse create(@Valid @RequestBody QrTemplateRequest request) {
        return qrTemplateService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER')")
    public QrTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody QrTemplateRequest request) {
        return qrTemplateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER')")
    public void delete(@PathVariable UUID id) {
        qrTemplateService.delete(id);
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER')")
    public QrTemplateResponse uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return qrTemplateService.uploadImage(id, file);
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasRole('SUPER')")
    public QrTemplateResponse deleteImage(@PathVariable UUID id) {
        return qrTemplateService.deleteImage(id);
    }

    /** Serves the template artwork. Public (like client logos) so {@code <img>} can load it. */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        StoredObject image = qrTemplateService.getImage(id);
        // The web client version-busts the URL on upload and the object key changes per upload.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(image.data());
    }
}
