package com.labresource.platform.storage.service;

import com.labresource.platform.storage.entity.StoredDocument;
import com.labresource.platform.storage.model.StoredFile;
import com.labresource.platform.storage.repository.StoredDocumentRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTests {

    @Mock
    private StoredDocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @TempDir
    Path tempStorageDir;

    private LocalStorageService storageService;
    private DocumentService documentService;

    private static final byte[] VALID_PDF_BYTES = new byte[]{
            0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x25, (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3
    };

    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    private static final byte[] VALID_JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
    };

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(tempStorageDir.toString(), "secret-test-key-32-bytes-long!!");
        documentService = new DocumentService(storageService, documentRepository, userRepository);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        assertThrows(ResponseStatusException.class, () ->
                documentService.uploadDocument(emptyFile, "CALIBRATION_CERTIFICATE", 1L, 5L));
    }

    @Test
    void rejectsFileExceeding10MB() {
        // Construct file larger than 10MB
        byte[] largeBytes = new byte[10 * 1024 * 1024 + 1024];
        System.arraycopy(VALID_PDF_BYTES, 0, largeBytes, 0, VALID_PDF_BYTES.length);

        MockMultipartFile oversizedFile = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                documentService.uploadDocument(oversizedFile, "CALIBRATION_CERTIFICATE", 1L, 5L));
        assertTrue(ex.getMessage().contains("exceeds maximum allowed limit of 10 MB"));
    }

    @Test
    void rejectsDisallowedMimeAndExtension() {
        MockMultipartFile exeFile = new MockMultipartFile("file", "danger.exe", "application/octet-stream", new byte[]{0x4D, 0x5A, 0x00, 0x00});
        assertThrows(ResponseStatusException.class, () ->
                documentService.uploadDocument(exeFile, "EQUIPMENT_MANUAL", 1L, 5L));
    }

    @Test
    void rejectsCorruptedOrFakePdfHeader() {
        byte[] fakeContent = "This is not a real PDF file".getBytes();
        MockMultipartFile fakePdf = new MockMultipartFile("file", "report.pdf", "application/pdf", fakeContent);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                documentService.uploadDocument(fakePdf, "MAINTENANCE_REPORT", 1L, 5L));
        assertTrue(ex.getMessage().contains("signature"));
    }

    @Test
    void uploadsValidPdfSuccessfully() {
        MockMultipartFile validPdf = new MockMultipartFile("file", "cert.pdf", "application/pdf", VALID_PDF_BYTES);
        when(documentRepository.save(any(StoredDocument.class))).thenAnswer(inv -> {
            StoredDocument doc = inv.getArgument(0);
            doc.setId(101L);
            return doc;
        });

        StoredDocument stored = documentService.uploadDocument(validPdf, "CALIBRATION_CERTIFICATE", 42L, 7L);

        assertNotNull(stored);
        assertEquals(101L, stored.getId());
        assertEquals("CALIBRATION_CERTIFICATE", stored.getEntityType());
        assertEquals(42L, stored.getEntityId());
        assertEquals("cert.pdf", stored.getFileName());
        assertNotNull(stored.getFileReference());
        assertNotNull(stored.getAccessUrl());
        verify(documentRepository).save(any(StoredDocument.class));
    }

    @Test
    void cleansUpStoredFileWhenDatabaseSaveFails() {
        MockMultipartFile validPdf = new MockMultipartFile("file", "cert.pdf", "application/pdf", VALID_PDF_BYTES);
        when(documentRepository.save(any(StoredDocument.class)))
                .thenThrow(new RuntimeException("Database error saving document row"));

        assertThrows(ResponseStatusException.class, () ->
                documentService.uploadDocument(validPdf, "CALIBRATION_CERTIFICATE", 42L, 7L));

        // Verify storage directory is empty (file was rolled back / deleted)
        String[] files = tempStorageDir.resolve("certificates").toFile().list();
        assertTrue(files == null || files.length == 0, "No orphaned files should remain after DB failure");
    }

    @Test
    void generatesValid10MinutePresignedUrlAndVerifiesSignature() {
        String fileRef = "certificates/cert-uuid_test.pdf";
        String presignedUrl = storageService.generatePresignedUrl(fileRef, Duration.ofMinutes(10));

        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.contains("expires="));
        assertTrue(presignedUrl.contains("sig="));

        // Parse query params
        String[] parts = presignedUrl.split("\\?");
        assertTrue(parts.length > 1);
        String[] queryPairs = parts[1].split("&");

        long expires = 0;
        String sig = null;
        for (String pair : queryPairs) {
            String[] kv = pair.split("=");
            if (kv[0].equals("expires")) expires = Long.parseLong(kv[1]);
            if (kv[0].equals("sig")) sig = kv[1];
        }

        assertTrue(expires > 0);
        assertNotNull(sig);
        assertTrue(storageService.verifySignature(fileRef, expires, sig));

        // Tampered signature fails
        assertFalse(storageService.verifySignature(fileRef, expires, sig + "tampered"));

        // Expired timestamp fails
        assertFalse(storageService.verifySignature(fileRef, System.currentTimeMillis() / 1000 - 60, sig));
    }
}
