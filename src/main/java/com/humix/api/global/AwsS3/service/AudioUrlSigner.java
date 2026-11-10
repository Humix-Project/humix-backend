package com.humix.api.global.AwsS3.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * private 버킷의 오디오를 읽을 수 있도록 저장된 S3 URL을 presigned GET URL로 바꾼다.
 * DB에는 AI 서버가 알려준 URL을 그대로 두고, 응답·요청을 내보내는 시점마다 서명한다 (서명 URL은 만료되므로 저장하지 않는다).
 */
@Component
@RequiredArgsConstructor
public class AudioUrlSigner {

    // 프론트 재생·파형 분석용
    static final Duration PLAYBACK_EXPIRATION = Duration.ofHours(1);

    // AI 서버가 원곡을 내려받는 용도. RunPod 콜드스타트·대기열을 감안해 결과 업로드용 PUT URL과 같은 30분
    static final Duration AI_SERVER_EXPIRATION = Duration.ofMinutes(30);

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String forPlayback(String storedUrl) {
        return sign(storedUrl, PLAYBACK_EXPIRATION);
    }

    public String forAiServer(String storedUrl) {
        return sign(storedUrl, AI_SERVER_EXPIRATION);
    }

    private String sign(String storedUrl, Duration expiration) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return storedUrl;
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(request -> request.bucket(bucket).key(extractKey(storedUrl)))
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // getPath()는 쿼리스트링을 제외하므로, 이미 서명된(쿼리가 붙은) URL이 저장돼 있어도 key만 뽑힌다.
    static String extractKey(String storedUrl) {
        String path = URI.create(storedUrl).getPath();
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
