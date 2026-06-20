package com.humix.api.domain.melodyScore.repository;

import com.humix.api.domain.melodyScore.entity.MelodyScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.ScopedValue;
import java.util.Optional;

public interface MelodyScoreRepository extends JpaRepository<MelodyScore, Long> {
    Optional<MelodyScore> findByHummingId(Long hummingId);
}
