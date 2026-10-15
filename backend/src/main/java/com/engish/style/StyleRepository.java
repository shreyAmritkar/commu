package com.engish.style;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StyleRepository extends JpaRepository<StyleProfile, Long> {
    List<StyleProfile> findAllByOrderByIdAsc();
}
