package com.labresource.platform.storage.model;

public record StoredFile(
        String fileReference,
        String originalFilename,
        String contentType,
        long size
) {}
