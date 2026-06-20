package com.humix.api.domain.upload.service;

import com.humix.api.domain.humming.dto.HummingDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService implements UploadServiceInterface { // 인터페이스 구현 보장

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Override
    public HummingDTO.AudioPresignedResponse getPresignedUrl(HummingDTO.AudioPresignedRequest request) {
        // 1. Content-Type 규격 검증
        if (request.contentType() == null || !request.contentType().startsWith("audio/")) {
            throw new IllegalArgumentException("올바르지 않은 오디오 Content-Type입니다.");
        }

        // 2. usage 분류에 따른 폴더 경로 설정
        String directory = "uploads/general";
        if ("HUMMING".equalsIgnoreCase(request.usage())) {
            directory = "uploads/humming";
        }

        // 3. UUID를 조합한 고유 파일 키 생성
        String uniqueFileName = UUID.randomUUID() + "_" + request.audioName();
        String fileKey = directory + "/" + uniqueFileName;

        // 4. AWS S3 전용 Presigned URL 생성 로직 기동
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(presignRequest);
        String presignedUrl = presignedPutObjectRequest.url().toString();

        return HummingDTO.AudioPresignedResponse.from(presignedUrl, fileKey);
    }
}