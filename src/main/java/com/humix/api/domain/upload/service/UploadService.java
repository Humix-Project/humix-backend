package com.humix.api.domain.upload.service;

import com.humix.api.domain.humming.dto.HummingDTO;
import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.humming.repository.HummingRepository;
import com.humix.api.domain.member.repository.MemberRepository;
import com.humix.api.domain.upload.dto.UploadDTO;
import com.humix.api.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UploadService implements UploadInterface {
    private final S3Presigner s3Presigner;
    private final HummingRepository hummingRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public UploadDTO.AudioPresignedResponse getPresignedUrl(UploadDTO.AudioPresignedRequest request) {
        String uniqueFileName = UUID.randomUUID() + "_" + request.audioName();
        String fileKey = "audio/" + uniqueFileName;

        // 1. HTTP PUT 요청을 위한 기본 오브젝트 리퀘스트 생성
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .contentType(request.contentType())
                .build();

        // 2. 만료 시간(5분) 및 발급 조건 설정
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest)
                .build();

        // 3. Presigned URL 발급
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String url = presignedRequest.url().toString();

        return UploadDTO.AudioPresignedResponse.from(url, fileKey);
    }

    @Transactional
    @Override
    public HummingDTO.HummingSaveResponse saveHummingInfo(CustomUserDetails userDetails,
                                                          HummingDTO.HummingSaveRequest request) {
        // 2. S3 실제 객체 표준 퍼블릭 URL 생성
        String s3FileUrl = String.format("https://%s.s3.amazonaws.com/%s", bucket, request.fileKey());

        // 3. HummingDTO.HummingSaveRequest 내부에 선언된 인스턴스 메서드를 활용해 엔티티 변환
        Humming humming = request.from(userDetails.getMember(), s3FileUrl);
        Humming savedHumming = hummingRepository.save(humming);

        // 4. HummingDTO.HummingSaveResponse의 정적 팩토리 메서드를 활용해 결과 반환
        return HummingDTO.HummingSaveResponse.from(savedHumming);
    }
}
