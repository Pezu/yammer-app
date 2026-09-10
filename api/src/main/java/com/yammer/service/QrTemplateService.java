package com.yammer.service;

import com.yammer.dto.QrTemplateRequest;
import com.yammer.dto.QrTemplateResponse;
import com.yammer.entity.QrTemplateEntity;
import com.yammer.repository.QrTemplateRepository;
import com.yammer.service.StorageService.StoredObject;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Global catalog of QR frames: a background image plus where the QR code and the order point name go. */
@Service
@RequiredArgsConstructor
public class QrTemplateService {

    private static final String IMAGE_PREFIX = "qr-templates";

    private final QrTemplateRepository qrTemplateRepository;
    private final StorageService storageService;

    public List<QrTemplateResponse> list() {
        return qrTemplateRepository.findAll(Sort.by("name")).stream()
                .map(QrTemplateResponse::from)
                .toList();
    }

    public QrTemplateResponse create(QrTemplateRequest request) {
        String name = request.name().trim();
        if (qrTemplateRepository.existsByNameIgnoreCase(name)) {
            throw conflict(name);
        }
        QrTemplateEntity entity = new QrTemplateEntity();
        entity.setName(name);
        applyGeometry(entity, request);
        return QrTemplateResponse.from(qrTemplateRepository.save(entity));
    }

    public QrTemplateResponse update(UUID id, QrTemplateRequest request) {
        QrTemplateEntity entity = qrTemplateRepository.findById(id).orElseThrow(() -> notFound(id));
        String name = request.name().trim();
        if (!name.equalsIgnoreCase(entity.getName()) && qrTemplateRepository.existsByNameIgnoreCase(name)) {
            throw conflict(name);
        }
        entity.setName(name);
        applyGeometry(entity, request);
        return QrTemplateResponse.from(qrTemplateRepository.save(entity));
    }

    /** Copy whichever geometry fields the request carries (null = keep). */
    private static void applyGeometry(QrTemplateEntity entity, QrTemplateRequest request) {
        if (request.qrX() != null) entity.setQrX(request.qrX());
        if (request.qrY() != null) entity.setQrY(request.qrY());
        if (request.qrSize() != null) entity.setQrSize(request.qrSize());
        if (request.labelY() != null) entity.setLabelY(request.labelY());
        if (request.labelSize() != null) entity.setLabelSize(request.labelSize());
        if (request.labelColor() != null) entity.setLabelColor(request.labelColor().toUpperCase());
    }

    public void delete(UUID id) {
        QrTemplateEntity entity = qrTemplateRepository.findById(id).orElseThrow(() -> notFound(id));
        qrTemplateRepository.delete(entity);
        storageService.delete(entity.getImageObject()); // best-effort; bundled art is a no-op
    }

    /** Replace the template's artwork with the uploaded image. */
    public QrTemplateResponse uploadImage(UUID id, MultipartFile file) {
        QrTemplateEntity entity = qrTemplateRepository.findById(id).orElseThrow(() -> notFound(id));
        String object = storageService.uploadImage(IMAGE_PREFIX, file);
        String previous = entity.getImageObject();
        entity.setImageObject(object);
        QrTemplateEntity saved = qrTemplateRepository.save(entity);
        storageService.delete(previous);
        return QrTemplateResponse.from(saved);
    }

    public QrTemplateResponse deleteImage(UUID id) {
        QrTemplateEntity entity = qrTemplateRepository.findById(id).orElseThrow(() -> notFound(id));
        String previous = entity.getImageObject();
        entity.setImageObject(null);
        QrTemplateEntity saved = qrTemplateRepository.save(entity);
        storageService.delete(previous);
        return QrTemplateResponse.from(saved);
    }

    /** The template's frame image bytes for serving, or 404 if it has none. */
    public StoredObject getImage(UUID id) {
        return getImage(qrTemplateRepository.findById(id).orElseThrow(() -> notFound(id)));
    }

    /** The frame image bytes of a loaded template, or 404 if it has none. */
    public StoredObject getImage(QrTemplateEntity entity) {
        if (entity.getImageObject() == null) {
            throw notFound(entity.getId());
        }
        return storageService.get(entity.getImageObject()).orElseThrow(() -> notFound(entity.getId()));
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "QR template not found: " + id);
    }

    private ResponseStatusException conflict(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "QR template already exists: " + name);
    }
}
