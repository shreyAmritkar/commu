package com.engish.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    List<Session> findTop50ByGuestIdOrderByCreatedAtDesc(String guestId);
}
