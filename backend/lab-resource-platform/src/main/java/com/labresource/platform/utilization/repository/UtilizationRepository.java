
package com.labresource.platform.utilization.repository;

import com.labresource.platform.utilization.entity.Utilization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UtilizationRepository extends JpaRepository<Utilization, Long> {

    List<Utilization> findByEquipmentId(Long equipmentId);

    List<Utilization> findByUserId(Long userId);

    List<Utilization> findByBookingId(Long bookingId);


    long countByEquipmentId(Long equipmentId);


    List<Utilization> findByStartTimeBetween(
            java.time.LocalDateTime start,
            java.time.LocalDateTime end
    );

}
