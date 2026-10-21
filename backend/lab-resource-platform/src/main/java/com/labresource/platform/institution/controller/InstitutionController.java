
package com.labresource.platform.institution.controller;

import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.InstitutionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/institutions")
public class InstitutionController {

    private final InstitutionRepository institutionRepository;

    public InstitutionController(
            InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    @GetMapping
    public List<Institution> getAllInstitutions() {
        return institutionRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Institution createInstitution(
            @RequestBody Institution institution) {
        institution.setId(null);
        return institutionRepository.save(institution);
    }
}
