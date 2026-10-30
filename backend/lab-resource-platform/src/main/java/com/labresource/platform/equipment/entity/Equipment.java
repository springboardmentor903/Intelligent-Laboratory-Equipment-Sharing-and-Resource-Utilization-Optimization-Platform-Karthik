package com.labresource.platform.equipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "lab_location", length = 150)
    private String labLocation;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(nullable = false, length = 30)
    private String status = "AVAILABLE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specifications", columnDefinition = "jsonb")
    private Map<String, Object> specifications;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "equipment_tags",
            joinColumns = @JoinColumn(name = "equipment_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_equipment_tags_equipment_tag",
                    columnNames = {"equipment_id", "tag"}
            )
    )
    @Column(name = "tag", nullable = false, length = 50)
    private Set<String> tags;
}
