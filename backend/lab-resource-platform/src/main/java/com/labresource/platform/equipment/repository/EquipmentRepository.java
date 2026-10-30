package com.labresource.platform.equipment.repository;

import com.labresource.platform.equipment.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    /** Serializes booking capacity checks for the same inventory row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    java.util.Optional<Equipment> findByIdForBooking(@Param("id") Long id);

    List<Equipment> findByInstitutionId(Long institutionId);

    List<Equipment> findByDepartmentId(Long departmentId);

    long countByDepartmentId(Long departmentId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Equipment e SET e.departmentId = null WHERE e.departmentId = :departmentId")
    void clearDepartmentId(@Param("departmentId") Long departmentId);

    List<Equipment> findByCategoryIgnoreCase(String category);

    List<Equipment> findByStatus(String status);

    List<Equipment> findByInstitutionIdAndStatus(
            Long institutionId,
            String status
    );

    @Query("""
            SELECT e FROM Equipment e
            WHERE (CAST(:name AS string) IS NULL OR TRIM(CAST(:name AS string)) = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))
              AND (CAST(:category AS string) IS NULL OR TRIM(CAST(:category AS string)) = '' OR LOWER(e.category) LIKE LOWER(CONCAT('%', CAST(:category AS string), '%')))
              AND (CAST(:description AS string) IS NULL OR TRIM(CAST(:description AS string)) = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:description AS string), '%')))
              AND (CAST(:labLocation AS string) IS NULL OR TRIM(CAST(:labLocation AS string)) = '' OR LOWER(e.labLocation) LIKE LOWER(CONCAT('%', CAST(:labLocation AS string), '%')))
              AND (CAST(:status AS string) IS NULL OR TRIM(CAST(:status AS string)) = '' OR LOWER(e.status) = LOWER(CAST(:status AS string)))
              AND (CAST(:tag AS string) IS NULL OR TRIM(CAST(:tag AS string)) = '' OR EXISTS (SELECT t FROM Equipment tagged JOIN tagged.tags t WHERE tagged.id = e.id AND LOWER(t) LIKE LOWER(CONCAT('%', CAST(:tag AS string), '%'))))
              AND (:institutionId IS NULL OR e.institutionId = :institutionId)
              AND (:departmentId IS NULL OR e.departmentId = :departmentId)
            """)
    List<Equipment> search(
            @Param("name") String name,
            @Param("category") String category,
            @Param("description") String description,
            @Param("labLocation") String labLocation,
            @Param("status") String status,
            @Param("tag") String tag,
            @Param("institutionId") Long institutionId,
            @Param("departmentId") Long departmentId
    );

    default List<Equipment> search(
            String name,
            String category,
            String description,
            String labLocation,
            String status,
            String tag,
            Long institutionId) {
        return search(name, category, description, labLocation, status, tag, institutionId, null);
    }
}
