package com.humix.api.domain.upload.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadDTO {
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
    ) {
        public static AudioPresignedResponse from(String presignedUrl, String fileKey) {
            return new AudioPresignedResponse(presignedUrl, fileKey);
        }
    }
}
