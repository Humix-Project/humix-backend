package com.humix.api.domain.humming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.humming.entity.ReferenceTrack;
import com.humix.api.domain.member.entity.Member;
import java.time.LocalDateTime;

public class HummingDTO {

    //Presigned URL 발급 요청 Request
    public record AudioPresignedRequest(
            @JsonProperty("audio_name") String audioName,
            @JsonProperty("content_type") String contentType,
            String usage
    ) {}

    //Presigned URL 발급 응답 Response
    public record AudioPresignedResponse(
            @JsonProperty("presigned_url") String presignedUrl,
            @JsonProperty("file_key") String fileKey
    ) {}

    //허밍 정보 저장 요청 Request
    public record HummingSaveRequest(
            @JsonProperty("file_key") String fileKey,
            @JsonProperty("duration_seconds") int durationSeconds
    ) {
        public Humming from(Member member, String s3FileUrl) {
            return Humming.builder()
                    .member(member)
                    .s3FileUrl(s3FileUrl)
                    .durationSeconds(this.durationSeconds)
                    .build();
        }
    }

    //허밍 정보 저장 응답 Response
    public record HummingSaveResponse(
            @JsonProperty("humming_id") Long hummingId,
            @JsonProperty("file_url") String fileUrl,
            @JsonProperty("duration_seconds") int durationSeconds,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {
        public static HummingSaveResponse from(Humming humming) {
            return new HummingSaveResponse(
                    humming.getId(),
                    humming.getS3FileUrl(),
                    humming.getDurationSeconds(),
                    humming.getCreatedAt()
            );
        }
    }
}
