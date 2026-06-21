package com.humix.api.domain.upload.controller;

import com.humix.api.domain.humming.dto.HummingDTO;
import com.humix.api.domain.upload.dto.UploadDTO;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/upload")
public interface UploadControllerDocs {

    @Operation(summary = "오디오 Presigned URL 발급 API", description = "S3에 오디오 파일을 업로드하기 위한 URL과 Key를 발급받습니다. (.mp3, .wav만 허용)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않는 확장자",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "확장자 에러",
                                    value = "{\"isSuccess\":false, \"code\":\"UPLOAD400\", \"message\":\"지원하지 않는 오디오 확장자입니다.\", \"result\":null}")))
    })
    @PostMapping("/audio/presigned")
    ApiResponse<UploadDTO.AudioPresignedResponse> getPresignedUrl(@RequestBody UploadDTO.AudioPresignedRequest request);

    @Operation(summary = "허밍 오디오 메타데이터 저장 API", description = "S3 업로드 완료 후 허밍 파일의 메타데이터를 서버에 저장합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    })
    @PostMapping("/humming")
    ApiResponse<HummingDTO.HummingSaveResponse> saveHummingInfo(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                @RequestBody HummingDTO.HummingSaveRequest request);

    @Operation(summary = "참조곡 오디오 메타데이터 저장 API", description = "S3 업로드 완료 후 사용자가 올린 참조곡의 메타데이터를 저장합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    })
    @PostMapping("/reference-tracks")
    ApiResponse<Object> saveReferenceTrackInfo(@RequestBody Object request);
}