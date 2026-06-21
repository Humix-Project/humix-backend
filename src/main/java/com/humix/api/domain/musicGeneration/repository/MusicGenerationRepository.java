package com.humix.api.domain.musicGeneration.repository;

import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MusicGenerationRepository extends JpaRepository<MusicGeneration, Long> {
    Optional<MusicGeneration> findByTaskId(String taskId);
    Page<MusicGeneration> findByMemberAndStatus(Member member, GenerationStatus status, Pageable pageable);
    Page<MusicGeneration> findByMemberAndGenreAndStatus(Member member, String genre, GenerationStatus status, Pageable pageable);
}
