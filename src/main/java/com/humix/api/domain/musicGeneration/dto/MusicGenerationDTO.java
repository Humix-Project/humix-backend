package com.humix.api.domain.musicGeneration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class MusicGenerationDTO {
    //노래 생성 요청 Request Body
    public record SongCreateRequest(
            @Schema(description = "허밍 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("humming_id") Long hummingId,

            @Schema(description = "생성할 곡 제목", example = "나의 첫 번째 곡")
            String title,

            @Schema(description = "장르 (예: pop, jazz, classical, hiphop 등)", example = "pop")
            String genre,

            @Schema(description = "분위기 (예: upbeat, calm, romantic, sad 등)", example = "upbeat")
            String mood,

            @Schema(description = "참조 트랙 ID (선택, 없으면 null)", example = "3", nullable = true)
            @JsonProperty("reference_track_id") Long referenceTrackId
    ) {
        //DTO를 MusicGeneration 엔티티로 변환하는 매핑 메서드
        public MusicGeneration from(Member member, MelodyScore melodyScore) {
            return MusicGeneration.builder()
                    .member(member)
                    .melodyScore(melodyScore)
                    .parentGeneration(null)
                    .name(this.title)
                    .genre(this.genre)
                    .atmosphere(this.mood)
                    .build();
        }
    }

    //비동기 작업 요청 접수 응답 공용 Response Body (202 Accepted)
    public record TaskAcceptedResponse(
            @Schema(description = "생성된 비동기 작업 ID", example = "task_550e8400-e29b-41d4-a716-446655440000")
            @JsonProperty("task_id") String taskId
    ) {}

    // 노래 생성 취소 요청 응답 Response Body
    public record TaskCancelResponse(
            @Schema(description = "취소된 비동기 작업 ID", example = "task_550e8400-e29b-41d4-a716-446655440000")
            @JsonProperty("task_id") String taskId,

            @Schema(description = "취소 후 작업 상태", example = "CANCELED")
            String status
    ) {}

    // 생성곡 프롬프트 수정 요청 Request Body
    public record SongModificationRequest(
            @Schema(description = "수정 방향 프롬프트 (예: 더 밝게, 템포를 빠르게 등)", example = "더 밝고 경쾌하게 수정해줘",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String prompt
    ) {
        //프롬프트 수정곡(수정 버전) 요청 시 새로운 MusicGeneration 엔티티를 생성하는 매핑 메서드
        public MusicGeneration from(Member member, MelodyScore melodyScore,
                                    MusicGeneration parentGeneration) {
            return MusicGeneration.builder()
                    .member(member)
                    .melodyScore(melodyScore)
                    .parentGeneration(parentGeneration)
                    .name(parentGeneration.getName() + " (수정본)")
                    .genre(parentGeneration.getGenre())
                    .atmosphere(parentGeneration.getAtmosphere())
                    .build();
        }
    }

    //SSE Event: progress 데이터 구조
    public record ProgressStreamResponse(
            @Schema(description = "작업 ID", example = "task_550e8400-e29b-41d4-a716-446655440000")
            @JsonProperty("task_id") String taskId,

            @Schema(description = "현재 작업 상태", example = "PROCESSING", allowableValues = {"PROCESSING", "COMPLETED", "FAILED", "CANCELED"})
            String status,

            @Schema(description = "진행률 (0~100)", example = "50")
            int progress
    ) {}

    //SSE Event: complete 내부 result 데이터 구조
    public record CompletionResult(
            @Schema(description = "작업 ID", example = "task_550e8400-e29b-41d4-a716-446655440000")
            @JsonProperty("task_id") String taskId,

            @Schema(description = "생성된 곡 ID", example = "42")
            @JsonProperty("song_id") Long songId,

            @Schema(description = "생성된 오디오 S3 URL", example = "https://humix-bucket.s3.amazonaws.com/audio/abc123_variation.wav")
            @JsonProperty("audio_url") String audioUrl,

            @Schema(description = "생성된 오디오 재생 시간 (초)", example = "30")
            @JsonProperty("duration_seconds") int durationSeconds
    ) {}

    //SSE Event: complete 데이터 구조
    public record CompletionStreamResponse(
            @Schema(description = "완료 상태", example = "COMPLETED")
            String status,

            @Schema(description = "완료 결과 데이터")
            CompletionResult result
    ) {}

    //곡 생성 요청 정보 상세 조회 Response Body
    public record GenerationDetailResponse(
            @JsonProperty("humming_duration_seconds") double hummingDurationSeconds,
            @JsonProperty("melody_note_count") int melodyNoteCount,
            String genre,
            String mood,
            @JsonProperty("reference_track") Object referenceTrack
    ) {
        //엔티티 및 연관 데이터(Humming 등)를 조합하여 조회 응답 DTO로 변환하는 메서드
        public static GenerationDetailResponse from(MusicGeneration musicGeneration,
                                                    double hummingDuration, int noteCount) {
            return new GenerationDetailResponse(
                    hummingDuration, //Humming 엔티티의 durationSeconds 등에서 조회
                    noteCount,       //MelodyScore 내부 notes_data JSON의 배열 길이 등에서 파싱
                    musicGeneration.getGenre(),
                    musicGeneration.getAtmosphere(), //엔티티의 atmosphere를 mood 필드로 매핑
                    null //기본값 null
            );
        }
    }

    //목록 조회 contents 내부에 담길 개별 곡 정보 레코드
    public record SongItemResponse(
            @Schema(description = "생성된 곡 ID", example = "42")
            @JsonProperty("song_id") Long songId,

            @Schema(description = "곡 제목", example = "나의 첫 번째 곡")
            String title,

            @Schema(description = "장르", example = "pop")
            String genre,

            @Schema(description = "원본 허밍 재생 시간 (초)", example = "12.5")
            @JsonProperty("humming_duration_seconds") double hummingDurationSeconds,

            @Schema(description = "생성된 오디오 재생 시간 (초)", example = "30")
            @JsonProperty("duration_seconds") int durationSeconds,

            @Schema(description = "생성된 오디오 S3 URL", example = "https://humix-bucket.s3.amazonaws.com/audio/abc123_variation.wav")
            @JsonProperty("audio_url") String audioUrl,

            @Schema(description = "생성 일시", example = "2026-06-21T18:00:00")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {
        //엔티티와 외부 조회 데이터를 조합하여 목록용 단일 객체로 변환하는 메서드
        public static SongItemResponse of(MusicGeneration musicGeneration, double hummingDuration,
                                          int durationSeconds) {
            return new SongItemResponse(
                    musicGeneration.getId(),
                    musicGeneration.getName(), //엔티티의 name을 title 필드로 매핑
                    musicGeneration.getGenre(),
                    hummingDuration,           //Humming 데이터에서 조회한 값
                    durationSeconds,          //AI 생성 후 오디오 재생 시간
                    musicGeneration.getResultS3Url(),
                    musicGeneration.getCreatedAt()
            );
        }
    }

    //사용자 생성 곡 목록 조회 전체 Response Body
    public record SongListResponse(
            @Schema(description = "전체 곡 수", example = "35")
            @JsonProperty("total_count") long totalCount,

            @Schema(description = "현재 페이지 번호 (1부터 시작)", example = "1")
            @JsonProperty("current_page") int currentPage,

            @Schema(description = "전체 페이지 수", example = "4")
            @JsonProperty("total_pages") int totalPages,

            @Schema(description = "현재 페이지에서 반환된 곡 수", example = "10")
            @JsonProperty("current_count") int currentCount,

            @Schema(description = "곡 목록")
            List<SongItemResponse> contents
    ) {
        //스프링 데이터 JPA의 Page 객체 정보 등을 받아 공용 목록 응답 DTO를 생성하는 정적 팩토리 메서드
        public static SongListResponse of(long totalCount, int currentPage,
                                          int totalPages, List<SongItemResponse> contents) {
            return new SongListResponse(
                    totalCount,
                    currentPage,
                    totalPages,
                    contents.size(), //반환하는 노래 개수를 자동으로 계산
                    contents
            );
        }
    }

    //AI 서버 작업 완료 콜백 Request Body
    public record AiTaskCompletionRequest(
            @JsonProperty("generated_audio_url") String generatedAudioUrl
    ) {
        public AiTaskCompletionRequest {
            if (generatedAudioUrl == null || generatedAudioUrl.isBlank()) {
                throw new IllegalArgumentException("생성된 오디오 URL(generated_audio_url)은 필수입니다.");
            }
        }
    }
}
