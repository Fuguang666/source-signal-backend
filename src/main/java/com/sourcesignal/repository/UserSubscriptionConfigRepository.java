package com.sourcesignal.repository;

import com.sourcesignal.entity.UserSubscriptionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSubscriptionConfigRepository extends JpaRepository<UserSubscriptionConfig, Long> {
    Optional<UserSubscriptionConfig> findByUserId(Long userId);
}
