package com.engish.character;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CharacterRepository extends JpaRepository<AiCharacter, Long> {
    @Query("select c from Scenario s join s.characters c where s.id = :scenarioId order by c.id")
    List<AiCharacter> findByScenarioId(@Param("scenarioId") Long scenarioId);
}
