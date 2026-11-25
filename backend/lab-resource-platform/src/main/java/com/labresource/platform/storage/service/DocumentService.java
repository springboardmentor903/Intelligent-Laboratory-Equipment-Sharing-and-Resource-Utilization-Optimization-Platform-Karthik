package com.labresource.platform.storage.service;

import com.labresource.platform.storage.entity.StoredDocument;
import com.labresource.platform.storage.model.StoredFile;
import com.labresource.platform.storage.repository.StoredDocumentRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L; // 10 MB
    public static final Duration PRESIGNED_URL_VALIDITY = Duration.ofMinutes(10); // 10 minutes

    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC_PREFIX = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final StorageService storageService;
    private final StoredDocumentRepository documentRepository;
    private final UserRepository userRepository;

    public DocumentService(StorageService storageService,
                           StoredDocumentRepository documentRepository,
                           UserRepository userRepository) {
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public StoredDocument uploadDocument(MultipartFile file, String entityType, Long entityId, Long userId) {
        validateFile(file);

        String normalizedType = normalizeEntityType(entityType);
        String folder = switch (normalizedType) {
            case "CALIBRATION_CERTIFICATE" -> "certificates";
            case "EQUIPMENT_MANUAL" -> "manuals";
            case "MAINTENANCE_REPORT" -> "reports";
            default -> "documents";
        };

        StoredFile storedFile = null;
        try {
            storedFile = storageService.store(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    folder
            );

            StoredDocument doc = new StoredDocument();
            doc.setEntityType(normalizedType);
            doc.setEntityId(entityId);
            doc.setFileName(storedFile.originalFilename());
            doc.setFileType(storedFile.contentType());
            doc.setFileSize(storedFile.size());
            doc.setFileReference(storedFile.fileReference());
            doc.setUploadedBy(userId);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());

            StoredDocument saved = documentRepository.save(doc);
            enrichDocument(saved);
            return saved;
        } catch (Exception ex) {
            // Clean up stored file to prevent orphaned storage on failure
            if (storedFile != null) {
                storageService.delete(storedFile.fileReference());
            }
            log.error("Failed to upload document for entity {} ({}): {}", normalizedType, entityId, ex.getMessage(), ex);
            if (ex instanceof ResponseStatusException) throw (ResponseStatusException) ex;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save document: " + ex.getMessage());
        }
    }

    @Transactional
    public StoredDocument replaceDocument(Long documentId, MultipartFile newFile, Long userId) {
        StoredDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        validateFile(newFile);
        String oldRef = doc.getFileReference();

        String folder = switch (doc.getEntityType()) {
            case "CALIBRATION_CERTIFICATE" -> "certificates";
            case "EQUIPMENT_MANUAL" -> "manuals";
            case "MAINTENANCE_REPORT" -> "reports";
            default -> "documents";
        };

        StoredFile storedFile = null;
        try {
            storedFile = storageService.store(
                    newFile.getInputStream(),
                    newFile.getOriginalFilename(),
                    newFile.getContentType(),
                    newFile.getSize(),
                    folder
            );

            doc.setFileName(storedFile.originalFilename());
            doc.setFileType(storedFile.contentType());
            doc.setFileSize(storedFile.size());
            doc.setFileReference(storedFile.fileReference());
            doc.setUploadedBy(userId);
            doc.setUpdatedAt(LocalDateTime.now());

            StoredDocument saved = documentRepository.save(doc);
            // Delete old file after successfully updating database
            storageService.delete(oldRef);

            enrichDocument(saved);
            return saved;
        } catch (Exception ex) {
            if (storedFile != null) {
                storageService.delete(storedFile.fileReference());
            }
            if (ex instanceof ResponseStatusException) throw (ResponseStatusException) ex;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to replace document: " + ex.getMessage());
        }
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        StoredDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        documentRepository.delete(doc);
        storageService.delete(doc.getFileReference());
    }

    public List<StoredDocument> getDocumentsForEntity(String entityType, Long entityId) {
        String normalizedType = normalizeEntityType(entityType);
        List<StoredDocument> docs = documentRepository.findByEntityTypeAndEntityId(normalizedType, entityId);
        docs.forEach(this::enrichDocument);
        return docs;
    }

    public StoredDocument getDocumentById(Long documentId) {
        StoredDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        enrichDocument(doc);
        return doc;
    }

    public String generatePresignedUrl(Long documentId) {
        StoredDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return storageService.generatePresignedUrl(doc.getFileReference(), PRESIGNED_URL_VALIDITY);
    }

    public Resource loadResourceByReference(String fileReference) {
        return storageService.loadAsResource(fileReference);
    }

    public boolean verifyAccessSignature(String fileReference, long expiresAt, String signature) {
        return storageService.verifySignature(fileReference, expiresAt, signature);
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required and cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File size (" + (file.getSize() / 1024 / 1024) + " MB) exceeds maximum allowed limit of 10 MB.");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";

        boolean isPdfExt = filename.endsWith(".pdf");
        boolean isPngExt = filename.endsWith(".png");
        boolean isJpegExt = filename.endsWith(".jpg") || filename.endsWith(".jpeg");

        boolean isPdfMime = "application/pdf".equals(contentType);
        boolean isPngMime = "image/png".equals(contentType);
        boolean isJpegMime = "image/jpeg".equals(contentType) || "image/jpg".equals(contentType);

        if (!((isPdfExt || isPdfMime) || (isPngExt || isPngMime) || (isJpegExt || isJpegMime))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Allowed file types are PDF, PNG, and JPEG. Received: " + (contentType.isBlank() ? filename : contentType));
        }

        // Verify magic byte content signature
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read < 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file: File header is corrupted or too short.");
            }

            boolean matchesPdf = startsWith(header, PDF_MAGIC);
            boolean matchesPng = read >= 8 && startsWith(header, PNG_MAGIC);
            boolean matchesJpeg = startsWith(header, JPEG_MAGIC_PREFIX);

            if (!matchesPdf && !matchesPng && !matchesJpeg) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "File content does not match the expected signature for PDF, PNG, or JPEG.");
            }

            if ((isPdfExt || isPdfMime) && !matchesPdf) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claimed PDF file does not have a valid %PDF header.");
            }
            if ((isPngExt || isPngMime) && !matchesPng) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claimed PNG file does not have a valid PNG header.");
            }
            if ((isJpegExt || isJpegMime) && !matchesJpeg) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claimed JPEG file does not have a valid JPEG header.");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read and verify file content: " + e.getMessage());
        }
    }

    private boolean startsWith(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) return false;
        }
        return true;
    }

    private String normalizeEntityType(String type) {
        if (type == null || type.isBlank()) return "GENERAL";
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CALIBRATION", "CALIBRATION_CERTIFICATE", "CERTIFICATE" -> "CALIBRATION_CERTIFICATE";
            case "MANUAL", "EQUIPMENT_MANUAL", "EQUIPMENT_DOCUMENT" -> "EQUIPMENT_MANUAL";
            case "MAINTENANCE", "MAINTENANCE_REPORT", "REPORT", "SERVICE_REPORT" -> "MAINTENANCE_REPORT";
            default -> normalized;
        };
    }

    private void enrichDocument(StoredDocument doc) {
        if (doc == null) return;
        doc.setAccessUrl(storageService.generatePresignedUrl(doc.getFileReference(), PRESIGNED_URL_VALIDITY));
        if (doc.getUploadedBy() != null) {
            userRepository.findById(doc.getUploadedBy()).ifPresent(u ->
                    doc.setUploaderName(u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail())
            );
        }
    }
}
