package com.humix.api.domain.humming.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

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
    ) {}

    //허밍 정보 저장 응답 Response
    public record HummingSaveResponse(
            @JsonProperty("humming_id") Long hummingId,
            @JsonProperty("file_url") String fileUrl,
            @JsonProperty("duration_seconds") int durationSeconds,
            @JsonProperty("created_at") OffsetDateTime createdAt
    ) {}
}
