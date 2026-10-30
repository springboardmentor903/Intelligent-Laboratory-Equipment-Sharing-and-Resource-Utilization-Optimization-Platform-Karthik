package com.labresource.platform.user.repository;

import com.labresource.platform.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);

    Optional<UserRole> findByUserIdAndRoleId(Long userId, Short roleId);

    boolean existsByUserIdAndRoleId(Long userId, Short roleId);

    boolean existsByRoleId(Short roleId);

    List<UserRole> findByRoleId(Short roleId);
}
