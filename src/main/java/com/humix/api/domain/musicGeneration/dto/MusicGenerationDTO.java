package com.humix.api.domain.musicGeneration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;

public class MusicGenerationDTO {
    //노래 생성 요청 Request Body
    public record SongCreateRequest(
            @JsonProperty("humming_id") Long hummingId,
            String title,
            String genre,
            String mood,
            @JsonProperty("reference_track_id") Long referenceTrackId
    ) {
        //DTO를 MusicGeneration 엔티티로 변환하는 매핑 메서드
        public MusicGeneration from(Member member, MelodyScore melodyScore) {
            return MusicGeneration.builder()
                    .member(member)
                    .melodyScore(melodyScore)
                    .parentGeneration(null)
                    .genre(this.genre)
                    .atmosphere(this.mood)
                    .build();
        }
    }

    //비동기 작업 요청 접수 응답 공용 Response Body (202 Accepted)
    public record TaskAcceptedResponse(
            @JsonProperty("task_id") String taskId
    ) {}

    // 노래 생성 취소 요청 응답 Response Body
    public record TaskCancelResponse(
            @JsonProperty("task_id") String taskId,
            String status // "CANCELED"
    ) {}

    // 생성곡 프롬프트 수정 요청 Request Body
    public record SongModificationRequest(
            String prompt
    ) {
        //프롬프트 수정곡(수정 버전) 요청 시 새로운 MusicGeneration 엔티티를 생성하는 매핑 메서드
        public MusicGeneration from(Member member, MelodyScore melodyScore, MusicGeneration parentGeneration) {
            return MusicGeneration.builder()
                    .member(member)
                    .melodyScore(melodyScore)
                    .parentGeneration(parentGeneration)
                    .genre(parentGeneration.getGenre())
                    .atmosphere(parentGeneration.getAtmosphere())
                    .build();
        }
    }

    //SSE Event: progress 데이터 구조
    public record ProgressStreamResponse(
            @JsonProperty("task_id") String taskId,
            String status,
            int progress
    ) {}

    //SSE Event: complete 내부 result 데이터 구조
    public record CompletionResult(
            @JsonProperty("task_id") String taskId,
            @JsonProperty("song_id") Long songId,
            @JsonProperty("audio_url") String audioUrl,
            @JsonProperty("duration_seconds") int durationSeconds
    ) {}

    //SSE Event: complete 데이터 구조
    public record CompletionStreamResponse(
            String status,
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
        public static GenerationDetailResponse from(MusicGeneration musicGeneration, double hummingDuration, int noteCount) {
            return new GenerationDetailResponse(
                    hummingDuration, //Humming 엔티티의 durationSeconds 등에서 조회
                    noteCount,       //MelodyScore 내부 notes_data JSON의 배열 길이 등에서 파싱
                    musicGeneration.getGenre(),
                    musicGeneration.getAtmosphere(), //엔티티의 atmosphere를 mood 필드로 매핑
                    null //기본값 null
            );
        }
    }
}
