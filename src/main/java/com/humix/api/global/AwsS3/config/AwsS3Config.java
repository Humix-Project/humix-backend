package com.humix.api.global.AwsS3.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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

    @Bean
    public S3Presigner s3Presigner() {
        System.out.println("===============================================");
        System.out.println("  AWS S3 Configuration Setup");
        System.out.println("  - Access Key ID : " + (accessKey != null ? accessKey : "Not Set"));
        System.out.println("===============================================");

        AwsCredentialsProvider credentialsProvider;
        if (accessKey == null || accessKey.isBlank() || accessKey.contains("placeholder")) {
            // Fallback to default credentials provider chain (environment variables, ~/.aws/credentials, etc.)
            credentialsProvider = DefaultCredentialsProvider.create();
        } else {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            credentialsProvider = StaticCredentialsProvider.create(credentials);
        }

        try {
            // 실제로 자격 증명 체인이 시스템에서 긁어온 Access Key ID를 확인하여 로그로 출력합니다.
            String resolvedKey = credentialsProvider.resolveCredentials().accessKeyId();
            System.out.println("  - Resolved AWS Access Key ID : " + resolvedKey);
        } catch (Exception e) {
            System.out.println("  - Resolved AWS Credentials ERROR: " + e.getMessage());
        }

        System.out.println("===============================================");

        // S3Presigner 빌더를 통해 Region과 자격 증명을 주입하여 Bean으로 등록합니다.
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
