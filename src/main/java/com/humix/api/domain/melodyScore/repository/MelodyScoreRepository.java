package com.humix.api.domain.melodyScore.repository;

import com.humix.api.domain.melodyScore.entity.MelodyScore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MelodyScoreRepository extends JpaRepository<MelodyScore, Long> {
}
