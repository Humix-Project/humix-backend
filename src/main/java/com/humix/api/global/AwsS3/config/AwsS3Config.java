package com.humix.api.global.AwsS3.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsS3Config {
    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${cloud.aws.profile:}")
    private String awsProfile;

    @Bean
    public S3Presigner s3Presigner() {
        AwsCredentialsProvider credentialsProvider;
        if (accessKey == null || accessKey.isBlank() || accessKey.contains("placeholder")) {
            if (awsProfile != null && !awsProfile.isBlank()) {
                // 특정 프로필이 명시적으로 지정되어 있으면 ProfileCredentialsProvider를 생성하여 사용합니다.
                credentialsProvider = ProfileCredentialsProvider.create(awsProfile);
            } else {
                // Fallback to default credentials provider chain (environment variables, ~/.aws/credentials, etc.)
                credentialsProvider = DefaultCredentialsProvider.create();
            }
        } else {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            credentialsProvider = StaticCredentialsProvider.create(credentials);
        }

        // S3Presigner 빌더를 통해 Region과 자격 증명을 주입하여 Bean으로 등록합니다.
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
