package com.humix.api.domain.upload.service;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UploadService {

    private final S3Presigner s3Presigner;
    private final MemberRepository memberRepository;
    private final HummingRepository hummingRepository;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.region-url}")
    private String regionUrl; // 예: https://humix-bucket.s3.ap-northeast-2.amazonaws.com

    /**
     * 1. AWS S3 Presigned URL 발급
     * 클라이언트가 S3 버킷의 hummings/ 폴더에 .wav 허밍 파일을 바로 업로드할 수 있는 10분 유효 서명 URL을 생성합니다.
     */
    public HummingDTO.AudioPresignedResponse createPresignedUrl(HummingDTO.AudioPresignedRequest request) {
        String directory = "general";
        if ("humming".equalsIgnoreCase(request.usage())) {
            directory = "hummings";
        }

        // 파일명 중복 및 덮어쓰기 방지를 위한 UUID 파일 키 조립
        String fileKey = directory + "/" + UUID.randomUUID() + "_" + request.audioName();

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

        return new HummingDTO.AudioPresignedResponse(presignedUrl, fileKey);
    }

    /**
     * 2. 허밍 메타데이터 정보 DB 영속화 저장
     * 자료구조 테이블 명세의 제약조건(uuid VARCHAR(36) FOREIGN KEY)을 충족하기 위해 String 타입의 uuid를 조회합니다.
     */
    @Transactional
    public HummingDTO.HummingSaveResponse saveHumming(String userUuid, HummingDTO.HummingSaveRequest request) {
        // 테이블 자료구조의 uuid(익명 사용자 고유 식별자) 기반 회원 조회
        Member member = memberRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션 사용자(UUID)입니다. 입력값: " + userUuid));

        // DB VARCHAR(512) 스펙 규격에 맞춰 S3 접근 전체 주소 URL 생성
        String s3FileUrl = String.format("%s/%s", regionUrl, request.fileKey());

        // HummingDTO 내부 레코드의 .from() 메서드를 호출해 도메인 엔티티 변환 및 저장
        Humming humming = request.from(member, s3FileUrl);
        Humming savedHumming = hummingRepository.save(humming);

        // 결과 DTO 반환
        return HummingDTO.HummingSaveResponse.from(savedHumming);
    }
}
