package com.humix.api.domain.musicGeneration.entity;

import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "music_generation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MusicGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uuid", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "melody_id", nullable = false)
    private MelodyScore melodyScore;

    // 자기 참조 (Self-Join): 원본 곡이 없는 최초 생성 시 NULL 허용
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_generation_id")
    private MusicGeneration parentGeneration;

    @Column(name = "name", length = 30, nullable = false)
    private String name;

    @Column(name = "genre", length = 30)
    private String genre;

    @Column(name = "atmosphere", length = 50)
    private String atmosphere;

    @Column(name = "result_s3_url", length = 512)
    private String resultS3Url;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private GenerationStatus status;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MusicGeneration(Member member, MelodyScore melodyScore, MusicGeneration parentGeneration,
                           String genre, String atmosphere) {
        this.member = member;
        this.melodyScore = melodyScore;
        this.parentGeneration = parentGeneration;
        this.name = "나의 허밍곡"; // Default 값 설정
        this.genre = genre;
        this.atmosphere = atmosphere;
        this.status = GenerationStatus.PROCESSING; // 초기 상태 설정
    }

    // 곡 이름 수정 메서드
    public void updateName(String name) {
        this.name = name;
    }

    // 완료 또는 실패 시 상태 및 결과 URL 업데이트 메서드
    public void updateStatus(GenerationStatus status, String resultS3Url) {
        this.status = status;
        this.resultS3Url = resultS3Url;
    }
}