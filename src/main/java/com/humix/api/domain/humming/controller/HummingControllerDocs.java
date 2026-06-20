package com.humix.api.domain.humming.controller;

import com.humix.api.domain.melodyScore.dto.MelodyScoreDTO;
import com.humix.api.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/hummings")
public interface HummingControllerDocs {

    @Operation(summary = "허밍을 멜로디 벡터로 변환 API", description = "저장된 허밍 오디오를 분석하여 편집 가능한 초기 멜로디 노트를 파싱합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "오디오 데이터 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "허밍 없음",
                                    value = "{\"isSuccess\":false, \"code\":\"HUMMING404\", \"message\":\"해당 허밍 데이터를 찾을 수 없습니다.\", \"result\":null}")))
    })
    @PostMapping("/{humming_id}/vectors")
    ApiResponse<MelodyScoreDTO.MelodyVectorResponse> convertHummingToVector(@PathVariable("humming_id") Long hummingId);

    @Operation(summary = "사용자 수정 멜로디 벡터 저장 API", description = "사용자가 웹 에디터에서 가공한 멜로디 노트 데이터를 업데이트합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    })
    @PutMapping("/{humming_id}/vectors")
    ApiResponse<MelodyScoreDTO.MelodyVectorResponse> updateHummingVector(
            @PathVariable("humming_id") Long hummingId,
            @RequestBody MelodyScoreDTO.MelodyUpdateRequest request);
}