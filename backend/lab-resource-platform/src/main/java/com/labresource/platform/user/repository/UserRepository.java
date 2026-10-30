package com.labresource.platform.user.repository;

import com.labresource.platform.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByInstitutionIdAndMembershipApprovedFalse(Long institutionId);

    List<User> findByInstitutionIdAndMembershipStatus(Long institutionId, String membershipStatus);

    List<User> findByMembershipStatus(String membershipStatus);

    List<User> findByRequestedRoleAndMembershipStatus(String requestedRole, String membershipStatus);

    List<User> findByInstitutionIdAndRequestedRoleAndMembershipStatus(Long institutionId, String requestedRole, String membershipStatus);

    List<User> findByDepartmentId(Long departmentId);

    long countByDepartmentId(Long departmentId);

    @Modifying
    @Query("UPDATE User u SET u.departmentId = null WHERE u.departmentId = :departmentId")
    void clearDepartmentId(@Param("departmentId") Long departmentId);
}
