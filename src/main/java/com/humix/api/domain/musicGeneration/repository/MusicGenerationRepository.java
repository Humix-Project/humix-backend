package com.humix.api.domain.musicGeneration.repository;

import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MusicGenerationRepository extends JpaRepository<MusicGeneration, Long> {
    Optional<MusicGeneration> findByTaskId(String taskId);
    Optional<MusicGeneration> findByIdAndDeletedAtIsNull(Long id);
    Optional<MusicGeneration> findByIdAndStatusAndDeletedAtIsNull(Long id, GenerationStatus status);
    Page<MusicGeneration> findByMemberAndStatusAndDeletedAtIsNull(Member member, GenerationStatus status, Pageable pageable);
    Page<MusicGeneration> findByMemberAndGenreAndStatusAndDeletedAtIsNull(Member member, String genre, GenerationStatus status, Pageable pageable);

    // 버전 목록: 같은 root의 유효한(완료 + soft delete 아님) 버전을 생성 순서대로
    List<MusicGeneration> findByRootGenerationIdAndStatusAndDeletedAtIsNullOrderByIdAsc(Long rootGenerationId, GenerationStatus status);

    boolean existsByRootGenerationIdAndStatusAndDeletedAtIsNull(Long rootGenerationId, GenerationStatus status);

    // 되돌리기: 기준 버전 이후(id가 더 큰) 같은 root의 버전을 모두 soft delete하고 삭제 건수를 반환
    @Modifying(clearAutomatically = true)
    @Query("update MusicGeneration g set g.deletedAt = :deletedAt " +
            "where g.rootGenerationId = :rootGenerationId and g.id > :generationId and g.deletedAt is null")
    int softDeleteVersionsAfter(@Param("rootGenerationId") Long rootGenerationId,
                                @Param("generationId") Long generationId,
                                @Param("deletedAt") LocalDateTime deletedAt);
}
