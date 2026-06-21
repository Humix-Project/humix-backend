package com.humix.api.domain.musicGeneration.controller;

import com.humix.api.domain.musicGeneration.dto.MusicGenerationDTO;
import com.humix.api.domain.musicGeneration.service.MusicGenerationService;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
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
            @RequestBody MusicGenerationDTO.AiTaskCompletionRequest request) {

        musicGenerationService.completeTask(taskId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}
