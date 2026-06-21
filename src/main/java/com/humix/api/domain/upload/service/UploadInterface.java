package com.humix.api.domain.upload.service;

import com.humix.api.domain.humming.dto.HummingDTO;
import com.humix.api.domain.upload.dto.UploadDTO;
import com.humix.api.global.security.userdetails.CustomUserDetails;

public interface UploadInterface {
    /**
     * S3에 오디오 파일을 업로드하기 위한 Presigned URL 및 파일 키(Key)를 생성합니다.
     *
     * @param request 파일명, Content-Type, 사용 목적(usage)이 담긴 요청 DTO
     * @return 발급된 Presigned URL과 S3 고유 파일 키를 담은 응답 DTO
     */
    UploadDTO.AudioPresignedResponse getPresignedUrl(UploadDTO.AudioPresignedRequest request);
    HummingDTO.HummingSaveResponse saveHummingInfo(CustomUserDetails userDetails,
                                                   HummingDTO.HummingSaveRequest request);
    /*
     * 특정 허밍 데이터를 삭제합니다. (DB 레코드 삭제 및 S3 물리 파일 삭제)
     *
     * @param userDetails 인증된 사용자 정보
     * @param hummingId 삭제할 허밍의 고유 ID
     */
    //void deleteHumming(CustomUserDetails userDetails, Long hummingId);
}
