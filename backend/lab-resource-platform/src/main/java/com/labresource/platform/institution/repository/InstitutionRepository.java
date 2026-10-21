
package com.labresource.platform.institution.repository;

import com.labresource.platform.institution.entity.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionRepository
        extends JpaRepository<Institution, Long> {
}
