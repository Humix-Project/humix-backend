package com.humix.api.domain.musicGeneration.controller;

import com.humix.api.domain.musicGeneration.dto.MusicGenerationDTO;
import com.humix.api.domain.musicGeneration.service.MusicGenerationService;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/tasks")
public class InternalController {

    private final MusicGenerationService musicGenerationService;

    @Operation(summary = "AI 서버 작업 완료 콜백 API", description = "AI 서버로부터 생성 완료된 오디오 파일 경로를 수신하여 태스크를 완료 처리합니다.")
    @PostMapping("/{task_id}/completion")
    public ApiResponse<Object> completeTask(
            @PathVariable("task_id") String taskId,
            @Valid @RequestBody MusicGenerationDTO.AiTaskCompletionRequest request) {

        musicGenerationService.completeTask(taskId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @Operation(summary = "AI 서버 진행 상황 콜백 API", description = "AI 서버가 단계마다 보내는 진행 상황을 수신해 SSE progress 이벤트로 전달합니다. 진행 중(PROCESSING)이 아닌 작업의 보고는 무시합니다.")
    @PostMapping("/{task_id}/progress")
    public ApiResponse<Object> reportProgress(
            @PathVariable("task_id") String taskId,
            @RequestBody MusicGenerationDTO.AiTaskProgressRequest request) {

        musicGenerationService.receiveProgress(taskId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}
