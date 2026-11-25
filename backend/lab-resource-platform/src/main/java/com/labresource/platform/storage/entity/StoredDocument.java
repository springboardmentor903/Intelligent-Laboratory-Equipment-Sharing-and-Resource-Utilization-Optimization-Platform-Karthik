package com.labresource.platform.storage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stored_documents", indexes = {
        @Index(name = "idx_stored_docs_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_stored_docs_ref", columnList = "file_reference"),
        @Index(name = "idx_stored_docs_uploader", columnList = "uploaded_by")
})
public class StoredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // 'CALIBRATION_CERTIFICATE', 'EQUIPMENT_MANUAL', 'MAINTENANCE_REPORT'

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType; // 'application/pdf', 'image/png', 'image/jpeg'

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_reference", nullable = false, length = 500)
    private String fileReference;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient
    private String accessUrl;

    @Transient
    private String uploaderName;
}
