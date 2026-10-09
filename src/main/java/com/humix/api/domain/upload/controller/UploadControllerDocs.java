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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "검증 실패",
                                    value = "{\"isSuccess\":false, \"code\":\"COMMON400\", \"message\":\"잘못된 요청입니다.\", \"result\":{\"audio_name\":\"audio_name은 필수입니다.\"}}")))
    })
    @PostMapping("/audio/presigned")
    ApiResponse<UploadDTO.AudioPresignedResponse> getPresignedUrl(@RequestBody UploadDTO.AudioPresignedRequest request);

    @Operation(summary = "허밍 오디오 메타데이터 저장 API",
            description = "S3 업로드 완료 후 허밍 파일의 메타데이터를 서버에 저장합니다.\n\n" +
                    "허밍은 최대 30초까지 가능하며, `duration_seconds`가 이를 넘으면 `HUMMING4001`(400)로 거부됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 허밍 길이 초과",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "검증 실패",
                                            value = "{\"isSuccess\":false, \"code\":\"COMMON400\", \"message\":\"잘못된 요청입니다.\", \"result\":{\"file_key\":\"file_key는 필수입니다.\"}}"),
                                    @ExampleObject(name = "허밍 길이 초과",
                                            value = "{\"isSuccess\":false, \"code\":\"HUMMING4001\", \"message\":\"허밍은 최대 30초까지 가능합니다.\", \"result\":null}")
                            }))
    })
    @PostMapping("/humming")
    ApiResponse<HummingDTO.HummingSaveResponse> saveHummingInfo(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                @RequestBody HummingDTO.HummingSaveRequest request);
}