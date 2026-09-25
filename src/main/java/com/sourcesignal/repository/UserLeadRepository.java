package com.sourcesignal.repository;

import com.sourcesignal.entity.UserLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLeadRepository extends JpaRepository<UserLead, Long> {
    Optional<UserLead> findByUserIdAndLeadId(Long userId, Long leadId);

    List<UserLead> findByUserIdAndMarkedTrue(Long userId);

    boolean existsByUserIdAndLeadId(Long userId, Long leadId);

    /** 统计用户已读线索数 */
    long countByUserIdAndReadTrue(Long userId);
}
