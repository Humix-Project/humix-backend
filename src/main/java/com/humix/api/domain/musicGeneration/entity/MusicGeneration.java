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

    @Column(name = "task_id", length = 100, unique = true)
    private String taskId;

    // 사용자가 입력한 스타일 프롬프트 (선택). 재생성 시 재사용한다.
    @Column(name = "prompt", length = 500)
    private String prompt;

    // 요청 시에는 요청한 길이, AI 완료 콜백 이후에는 AI가 보고한 실제 길이
    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private GenerationStatus status;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MusicGeneration(Member member, MelodyScore melodyScore, MusicGeneration parentGeneration,
                           String name, String genre, String atmosphere, String prompt, String taskId,
                           Double durationSeconds) {
        this.member = member;
        this.melodyScore = melodyScore;
        this.parentGeneration = parentGeneration;
        this.name = name != null ? name : "나의 허밍곡"; // Default 값 설정
        this.genre = genre;
        this.atmosphere = atmosphere;
        this.prompt = prompt;
        this.taskId = taskId;
        this.durationSeconds = durationSeconds;
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

    // Task ID 업데이트 메서드
    public void updateTaskId(String taskId) {
        this.taskId = taskId;
    }

    // 재생 시간 업데이트 메서드
    public void updateDuration(Double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}