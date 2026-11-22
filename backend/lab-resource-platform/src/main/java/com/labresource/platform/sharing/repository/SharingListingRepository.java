package com.labresource.platform.sharing.repository;

import java.util.List;
import java.util.Optional;
import com.labresource.platform.sharing.entity.SharingListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SharingListingRepository extends JpaRepository<SharingListing, Long> {

    Optional<SharingListing> findByEquipmentId(Long id);

    List<SharingListing> findByEquipmentIdIn(List<Long> equipmentIds);

    @Query(value = """
            select l from SharingListing l, Equipment e
            where e.id = l.equipmentId
              and l.active = true
              and (:owner is null or e.institutionId = :owner)
              and (cast(:category as string) is null or lower(e.category) = lower(cast(:category as string)))
              and (cast(:keyword as string) is null or lower(e.name) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(coalesce(e.description, '')) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(coalesce(e.assetTag, '')) like lower(concat('%', cast(:keyword as string), '%')))
              and (:exclude is null or e.institutionId <> :exclude)
            """,
           countQuery = """
            select count(l) from SharingListing l, Equipment e
            where e.id = l.equipmentId
              and l.active = true
              and (:owner is null or e.institutionId = :owner)
              and (cast(:category as string) is null or lower(e.category) = lower(cast(:category as string)))
              and (cast(:keyword as string) is null or lower(e.name) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(coalesce(e.description, '')) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(coalesce(e.assetTag, '')) like lower(concat('%', cast(:keyword as string), '%')))
              and (:exclude is null or e.institutionId <> :exclude)
            """)
    Page<SharingListing> discover(
            @Param("owner") Long owner,
            @Param("category") String category,
            @Param("keyword") String keyword,
            @Param("exclude") Long exclude,
            Pageable pageable);
}
