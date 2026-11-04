package com.labresource.platform.equipment.repository;

import com.labresource.platform.equipment.entity.Equipment;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class EquipmentSpecifications {

    private EquipmentSpecifications() {
    }

    public static Specification<Equipment> forLiveMonitoring(
            Long institutionId,
            String status,
            String query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (institutionId != null) {
                predicates.add(criteriaBuilder.equal(root.get("institutionId"), institutionId));
            }

            if (status != null && !status.isBlank()) {
                String normalizedStatus = status.trim().toUpperCase();
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("status")),
                        normalizedStatus
                ));
            }

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                Predicate nameMatch = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("name")),
                        pattern
                );
                Expression<String> assetTagCoalesce = criteriaBuilder.coalesce(root.get("assetTag"), "");
                Predicate assetTagMatch = criteriaBuilder.like(
                        criteriaBuilder.lower(assetTagCoalesce),
                        pattern
                );
                predicates.add(criteriaBuilder.or(nameMatch, assetTagMatch));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
