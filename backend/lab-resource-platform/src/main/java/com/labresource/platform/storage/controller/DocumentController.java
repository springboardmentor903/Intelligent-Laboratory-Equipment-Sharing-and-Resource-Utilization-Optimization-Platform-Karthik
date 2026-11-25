package com.labresource.platform.storage.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.storage.entity.StoredDocument;
import com.labresource.platform.storage.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ResourceAccessPolicy resourceAccess;

    public DocumentController(DocumentService documentService, ResourceAccessPolicy resourceAccess) {
        this.documentService = documentService;
        this.resourceAccess = resourceAccess;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<StoredDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("entityType") String entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            Authentication authentication) {
        Long currentUserId = resourceAccess.currentUserId(authentication);
        StoredDocument doc = documentService.uploadDocument(file, entityType, entityId, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<StoredDocument> replaceDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long currentUserId = resourceAccess.currentUserId(authentication);
        StoredDocument doc = documentService.replaceDocument(id, file, currentUserId);
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<StoredDocument>> getDocumentsForEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        return ResponseEntity.ok(documentService.getDocumentsForEntity(entityType, entityId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<StoredDocument> getDocumentById(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @GetMapping("/{id}/access-url")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getPresignedAccessUrl(@PathVariable Long id) {
        StoredDocument doc = documentService.getDocumentById(id);
        String accessUrl = documentService.generatePresignedUrl(id);
        return ResponseEntity.ok(Map.of(
                "id", doc.getId(),
                "fileName", doc.getFileName(),
                "accessUrl", accessUrl,
                "validMinutes", 10,
                "validSeconds", 600
        ));
    }

    @GetMapping("/raw")
    public ResponseEntity<Resource> servePresignedFile(
            @RequestParam("ref") String encodedRef,
            @RequestParam("expires") long expiresAt,
            @RequestParam("sig") String signature) {
        String fileReference = URLDecoder.decode(encodedRef, StandardCharsets.UTF_8);

        if (!documentService.verifyAccessSignature(fileReference, expiresAt, signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired document access link.");
        }

        Resource resource = documentService.loadResourceByReference(fileReference);
        String filename = resource.getFilename() != null ? resource.getFilename() : "document";
        String contentType = determineContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        StoredDocument doc = documentService.getDocumentById(id);
        Resource resource = documentService.loadResourceByReference(doc.getFileReference());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getFileType() != null ? doc.getFileType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    private String determineContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
