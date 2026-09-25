package com.sourcesignal.repository;

import com.sourcesignal.entity.UserPushChannel;
import com.sourcesignal.enums.PushChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPushChannelRepository extends JpaRepository<UserPushChannel, Long> {
    List<UserPushChannel> findByUserId(Long userId);

    Optional<UserPushChannel> findByUserIdAndChannel(Long userId, PushChannelType channel);

    List<UserPushChannel> findByUserIdAndEnabledTrue(Long userId);
}
