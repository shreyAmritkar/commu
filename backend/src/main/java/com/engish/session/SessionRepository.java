package com.engish.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    List<Session> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    // @Modifying(clearAutomatically = true)
    // @Query("update Session s set s.userId = :userId, s.guestId = null where s.guestId = :guestId and s.userId is null")
    // int claimGuest(@Param("guestId") String guestId, @Param("userId") UUID userId);
}