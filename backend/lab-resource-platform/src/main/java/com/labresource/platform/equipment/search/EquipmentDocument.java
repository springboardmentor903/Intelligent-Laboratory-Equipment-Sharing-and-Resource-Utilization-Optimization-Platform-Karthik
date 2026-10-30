package com.labresource.platform.equipment.search;

import com.labresource.platform.equipment.entity.Equipment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "equipment")
public class EquipmentDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Long)
    private Long institutionId;

    @Field(type = FieldType.Long)
    private Long departmentId;

    @Field(type = FieldType.Keyword)
    private String labLocation;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Integer)
    private Integer quantity;

    @Field(type = FieldType.Keyword)
    private Set<String> tags;

    public static EquipmentDocument fromEntity(Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        return EquipmentDocument.builder()
                .id(equipment.getId())
                .name(equipment.getName())
                .category(equipment.getCategory())
                .description(equipment.getDescription())
                .institutionId(equipment.getInstitutionId())
                .departmentId(equipment.getDepartmentId())
                .labLocation(equipment.getLabLocation())
                .status(equipment.getStatus())
                .quantity(equipment.getQuantity())
                .tags(equipment.getTags() != null ? new HashSet<>(equipment.getTags()) : Collections.emptySet())
                .build();
    }
}
