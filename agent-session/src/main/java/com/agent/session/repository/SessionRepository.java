package com.agent.session.repository;

import com.agent.session.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    Optional<Session> findBySessionId(String sessionId);

    @Modifying
    @Query("DELETE FROM Session s WHERE s.sessionId = :sessionId")
    int deleteBySessionId(String sessionId);

    List<Session> findByTenantIdAndUserIdAndStatus(Long tenantId, String userId, Integer status);
}
