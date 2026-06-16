package com.humix.api.domain.humming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.humix.api.domain.humming.entity.ReferenceTrack;
import com.humix.api.domain.member.entity.Member;

import java.time.LocalDateTime;

public class ReferenceTrackDTO {
    //참조곡 업로드 후 정보 저장 요청
    public record ReferenceTrackSaveRequest(
            @JsonProperty("file_key") String fileKey,
            @JsonProperty("audio_name") String audioName,
            @JsonProperty("duration_seconds") int durationSeconds
    ) {
        // DTO를 ReferenceTrack 엔티티로 변환하는 매핑 메서드
        public ReferenceTrack from(Member member, String s3FileUrl, String audioName, int durationSeconds) {
            return ReferenceTrack.builder()
                    .member(member)
                    .audioName(audioName)
                    .s3FileUrl(s3FileUrl)
                    .durationSeconds(durationSeconds)
                    .build();
        }
    }

    //참조곡 업로드 후 정보 저장 응답
    public record ReferenceTrackSaveResponse(
            @JsonProperty("reference_track_id") Long referenceTrackId,
            @JsonProperty("audio_name") String audioName,
            @JsonProperty("file_url") String fileUrl,
            @JsonProperty("duration_seconds") int durationSeconds,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {
        //ReferenceTrack 엔티티를 응답 DTO로 변환하는 메서드
        public static ReferenceTrackSaveResponse from(ReferenceTrack referenceTrack) {
            return new ReferenceTrackSaveResponse(
                    referenceTrack.getId(),
                    referenceTrack.getAudioName(),
                    referenceTrack.getS3FileUrl(),
                    referenceTrack.getDurationSeconds(),
                    referenceTrack.getCreatedAt()
            );
        }
    }
}
