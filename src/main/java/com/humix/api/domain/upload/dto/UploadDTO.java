package com.humix.api.domain.upload.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class UploadDTO {
    //Presigned URL 발급 요청 Request
    public record AudioPresignedRequest(
            @NotBlank(message = "audio_name은 필수입니다.")
            @JsonProperty("audio_name") String audioName,
            @NotBlank(message = "content_type은 필수입니다.")
            @JsonProperty("content_type") String contentType,
            String usage
    ) {}

    //Presigned URL 발급 응답 Response
    public record AudioPresignedResponse(
            @JsonProperty("presigned_url") String presignedUrl,
            @JsonProperty("file_key") String fileKey
    ) {
        public static AudioPresignedResponse from(String presignedUrl, String fileKey) {
            return new AudioPresignedResponse(presignedUrl, fileKey);
        }
    }
}
