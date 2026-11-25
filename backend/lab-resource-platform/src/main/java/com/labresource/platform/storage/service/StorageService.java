package com.labresource.platform.storage.service;

import com.labresource.platform.storage.model.StoredFile;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    StoredFile store(InputStream stream, String originalFilename, String contentType, long size, String folder);

    void delete(String fileReference);

    String generatePresignedUrl(String fileReference, Duration validity);

    Resource loadAsResource(String fileReference);

    boolean verifySignature(String fileReference, long expiresAt, String signature);
}
