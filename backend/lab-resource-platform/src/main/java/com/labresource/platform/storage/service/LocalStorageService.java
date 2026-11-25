package com.labresource.platform.storage.service;

import com.labresource.platform.storage.model.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path rootLocation;
    private final byte[] signingKey;

    public LocalStorageService(
            @Value("${app.storage.dir:./uploads}") String storageDir,
            @Value("${jwt.secret:Az4QgClB/R8osKj42wsav5RSBc2Qpld2UK52lzjE+L4=}") String secret) {
        this.rootLocation = Paths.get(storageDir).toAbsolutePath().normalize();
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            log.error("Could not initialize storage directory at {}", this.rootLocation, e);
        }
    }

    @Override
    public StoredFile store(InputStream stream, String originalFilename, String contentType, long size, String folder) {
        String cleanFolder = folder != null && !folder.isBlank()
                ? folder.replaceAll("[^a-zA-Z0-9_-]", "")
                : "general";

        Path folderPath = rootLocation.resolve(cleanFolder).normalize();
        try {
            Files.createDirectories(folderPath);

            String safeFilename = sanitizeFilename(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString().substring(0, 12) + "_" + safeFilename;
            Path destination = folderPath.resolve(uniqueFilename).normalize();

            // Prevent path traversal
            if (!destination.startsWith(rootLocation)) {
                throw new SecurityException("Cannot store file outside current storage directory.");
            }

            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);

            String fileReference = cleanFolder + "/" + uniqueFilename;
            log.info("Stored file successfully: {}", fileReference);
            return new StoredFile(fileReference, safeFilename, contentType, size);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + originalFilename, e);
        }
    }

    @Override
    public void delete(String fileReference) {
        if (fileReference == null || fileReference.isBlank()) return;
        try {
            Path file = rootLocation.resolve(fileReference).normalize();
            if (file.startsWith(rootLocation) && Files.exists(file)) {
                Files.delete(file);
                log.info("Deleted stored file: {}", fileReference);
            }
        } catch (IOException e) {
            log.warn("Failed to delete stored file: {}", fileReference, e);
        }
    }

    @Override
    public String generatePresignedUrl(String fileReference, Duration validity) {
        long expiresAt = Instant.now().plus(validity).getEpochSecond();
        String signature = computeSignature(fileReference, expiresAt);
        return "/api/documents/raw?ref=" + URLEncoder.encode(fileReference, StandardCharsets.UTF_8)
                + "&expires=" + expiresAt
                + "&sig=" + signature;
    }

    @Override
    public Resource loadAsResource(String fileReference) {
        try {
            Path file = rootLocation.resolve(fileReference).normalize();
            if (!file.startsWith(rootLocation)) {
                throw new SecurityException("Cannot access file outside storage root.");
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + fileReference);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed file URL: " + fileReference, e);
        }
    }

    @Override
    public boolean verifySignature(String fileReference, long expiresAt, String signature) {
        if (fileReference == null || signature == null) return false;
        if (Instant.now().getEpochSecond() > expiresAt) {
            log.warn("Presigned URL expired for reference: {}", fileReference);
            return false;
        }

        String expectedSignature = computeSignature(fileReference, expiresAt);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String computeSignature(String fileReference, long expiresAt) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(signingKey, "HmacSHA256");
            hmac.init(keySpec);
            String payload = fileReference + ":" + expiresAt;
            byte[] rawHmac = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "document.bin";
        String name = Paths.get(filename).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
