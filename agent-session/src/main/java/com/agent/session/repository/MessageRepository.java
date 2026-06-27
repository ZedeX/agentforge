package com.agent.session.repository;

import com.agent.session.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    List<Message> findBySessionId(String sessionId);

    Page<Message> findBySessionId(String sessionId, Pageable pageable);
}
