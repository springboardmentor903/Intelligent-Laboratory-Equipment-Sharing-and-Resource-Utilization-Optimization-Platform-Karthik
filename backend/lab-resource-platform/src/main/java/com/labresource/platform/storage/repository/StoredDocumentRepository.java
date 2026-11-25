package com.labresource.platform.storage.repository;

import com.labresource.platform.storage.entity.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoredDocumentRepository extends JpaRepository<StoredDocument, Long> {

    List<StoredDocument> findByEntityTypeAndEntityId(String entityType, Long entityId);

    Optional<StoredDocument> findByFileReference(String fileReference);

    List<StoredDocument> findByEntityId(Long entityId);

    void deleteByEntityTypeAndEntityId(String entityType, Long entityId);
}
