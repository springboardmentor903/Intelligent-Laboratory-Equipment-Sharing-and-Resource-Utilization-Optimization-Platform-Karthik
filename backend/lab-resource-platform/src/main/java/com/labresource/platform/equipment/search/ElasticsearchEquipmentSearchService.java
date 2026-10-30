package com.labresource.platform.equipment.search;

import com.labresource.platform.equipment.entity.Equipment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class ElasticsearchEquipmentSearchService implements EquipmentSearchService {

    private final Optional<ElasticsearchOperations> operations;
    private final boolean enabled;

    public ElasticsearchEquipmentSearchService(
            @Autowired(required = false) ElasticsearchOperations operations,
            @Value("${elasticsearch.enabled:false}") boolean enabled
    ) {
        this.operations = Optional.ofNullable(operations);
        this.enabled = enabled;
        if (this.enabled && this.operations.isPresent()) {
            log.info("Elasticsearch equipment search integration is ENABLED");
        } else {
            log.info("Elasticsearch equipment search integration is DISABLED (relational fallback active)");
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled && operations.isPresent();
    }

    @Override
    public void indexEquipment(Equipment equipment) {
        if (!isEnabled() || equipment == null || equipment.getId() == null) {
            return;
        }
        try {
            EquipmentDocument doc = EquipmentDocument.fromEntity(equipment);
            operations.get().save(doc);
            log.debug("Indexed equipment ID {} in Elasticsearch", equipment.getId());
        } catch (Exception ex) {
            log.warn("Failed to index equipment ID {} in Elasticsearch: {}", equipment.getId(), ex.getMessage());
        }
    }

    @Override
    public void deleteEquipment(Long equipmentId) {
        if (!isEnabled() || equipmentId == null) {
            return;
        }
        try {
            operations.get().delete(String.valueOf(equipmentId), EquipmentDocument.class);
            log.debug("Deleted equipment ID {} from Elasticsearch", equipmentId);
        } catch (Exception ex) {
            log.warn("Failed to delete equipment ID {} from Elasticsearch: {}", equipmentId, ex.getMessage());
        }
    }

    @Override
    public Optional<List<Long>> searchEquipmentIds(
            String name,
            String category,
            String description,
            String labLocation,
            String status,
            Long institutionId,
            Long departmentId,
            String tag
    ) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try {
            Criteria criteria = new Criteria();
            boolean hasCriteria = false;

            if (name != null && !name.isBlank()) {
                criteria = criteria.and(new Criteria("name").contains(name.trim()));
                hasCriteria = true;
            }
            if (category != null && !category.isBlank()) {
                criteria = criteria.and(new Criteria("category").is(category.trim()));
                hasCriteria = true;
            }
            if (description != null && !description.isBlank()) {
                criteria = criteria.and(new Criteria("description").contains(description.trim()));
                hasCriteria = true;
            }
            if (labLocation != null && !labLocation.isBlank()) {
                criteria = criteria.and(new Criteria("labLocation").is(labLocation.trim()));
                hasCriteria = true;
            }
            if (status != null && !status.isBlank()) {
                criteria = criteria.and(new Criteria("status").is(status.trim().toUpperCase()));
                hasCriteria = true;
            }
            if (institutionId != null) {
                criteria = criteria.and(new Criteria("institutionId").is(institutionId));
                hasCriteria = true;
            }
            if (departmentId != null) {
                criteria = criteria.and(new Criteria("departmentId").is(departmentId));
                hasCriteria = true;
            }
            if (tag != null && !tag.isBlank()) {
                criteria = criteria.and(new Criteria("tags").is(tag.trim().toLowerCase()));
                hasCriteria = true;
            }

            CriteriaQuery query = hasCriteria ? new CriteriaQuery(criteria) : new CriteriaQuery(new Criteria());
            SearchHits<EquipmentDocument> hits = operations.get().search(query, EquipmentDocument.class);

            List<Long> ids = hits.getSearchHits().stream()
                    .map(h -> h.getContent() != null ? h.getContent().getId() : null)
                    .filter(Objects::nonNull)
                    .toList();

            return Optional.of(ids);
        } catch (Exception ex) {
            log.warn("Elasticsearch query failed ({}), falling back to relational search", ex.getMessage());
            return Optional.empty();
        }
    }
}
