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
@Table(name = "music_generation", indexes = @Index(name = "idx_music_generation_root", columnList = "root_generation_id"))
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

    // 같은 곡의 버전 묶음 기준. 원본은 자기 자신의 ID, 수정본은 부모의 root를 물려받는다.
    // 이 컬럼이 생기기 전에 만든 곡은 NULL이며, 배포 후 백필 SQL로 자기 자신의 ID를 채운다 (infra/db/ERD.md 참고).
    @Column(name = "root_generation_id")
    private Long rootGenerationId;

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

    // 되돌리기로 무효화된 버전의 soft delete 시각. NULL이면 유효
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public MusicGeneration(Member member, MelodyScore melodyScore, MusicGeneration parentGeneration,
                           String name, String genre, String atmosphere, String prompt, String taskId,
                           Double durationSeconds) {
        this.member = member;
        this.melodyScore = melodyScore;
        this.parentGeneration = parentGeneration;
        this.rootGenerationId = parentGeneration != null ? parentGeneration.getRootGenerationId() : null;
        this.name = name != null ? name : "나의 허밍곡"; // Default 값 설정
        this.genre = genre;
        this.atmosphere = atmosphere;
        this.prompt = prompt;
        this.taskId = taskId;
        this.durationSeconds = durationSeconds;
        this.status = GenerationStatus.PROCESSING; // 초기 상태 설정
    }

    // 최초 생성곡을 자기 자신의 root로 지정. IDENTITY 전략이라 save() 이후에 ID가 확정되므로 별도 호출이 필요하다.
    public void markAsRoot() {
        this.rootGenerationId = this.id;
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