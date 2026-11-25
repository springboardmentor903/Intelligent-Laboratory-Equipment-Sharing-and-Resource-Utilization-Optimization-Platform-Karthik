package com.labresource.platform.notification.repository;

import com.labresource.platform.notification.entity.PushDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {

    List<PushDeviceToken> findByUserId(Long userId);

    Optional<PushDeviceToken> findByUserIdAndToken(Long userId, String token);

    void deleteByUserIdAndToken(Long userId, String token);

    boolean existsByUserIdAndToken(Long userId, String token);
}
