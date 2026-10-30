package com.labresource.platform.institution.repository;

import com.labresource.platform.institution.entity.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstitutionRepository
        extends JpaRepository<Institution, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<Institution> findByNameIgnoreCase(String name);
    List<Institution> findByStatus(String status);
}
