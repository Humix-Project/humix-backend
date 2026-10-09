package com.humix.api.domain.musicGeneration.controller;

import com.humix.api.domain.musicGeneration.dto.MusicGenerationDTO;
import com.humix.api.domain.musicGeneration.service.MusicGenerationService;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import com.humix.api.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/generation/songs")
public class GenerationController implements GenerationControllerDocs {

    private final MusicGenerationService musicGenerationService;

    // ⑧ 노래 생성 요청 (비동기)
    @PostMapping("")
    @Override
    public ApiResponse<MusicGenerationDTO.TaskAcceptedResponse> generateSong(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MusicGenerationDTO.SongCreateRequest request) {
        
        MusicGenerationDTO.TaskAcceptedResponse result = musicGenerationService.generateSong(userDetails, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.ACCEPTED, result);
    }

    // ⑨ & ⑫ 진행 상황 구독 (SSE)
    @GetMapping(value = "/tasks/{task_id}/stream", produces = "text/event-stream")
    @Override
    public SseEmitter subscribeTaskStream(@PathVariable("task_id") String taskId) {
        return musicGenerationService.subscribeTaskStream(taskId);
    }

    // ⑩ 비동기 작업 취소
    @DeleteMapping("/tasks/{task_id}")
    @Override
    public ApiResponse<MusicGenerationDTO.TaskCancelResponse> cancelTask(@PathVariable("task_id") String taskId) {
        MusicGenerationDTO.TaskCancelResponse result = musicGenerationService.cancelTask(taskId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }

    // ⑪ 생성곡 프롬프트 수정 요청
    @PostMapping("/{song_id}/modifications")
    @Override
    public ApiResponse<MusicGenerationDTO.TaskAcceptedResponse> modifySongPrompt(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("song_id") Long songId,
            @Valid @RequestBody MusicGenerationDTO.SongModificationRequest request) {
        
        MusicGenerationDTO.TaskAcceptedResponse result = musicGenerationService.modifySongPrompt(userDetails, songId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.ACCEPTED, result);
    }

    // 특정 버전으로 되돌리기 (이후 버전 soft delete)
    @PostMapping("/{generation_id}/revert")
    @Override
    public ApiResponse<MusicGenerationDTO.RevertResponse> revertToVersion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("generation_id") Long generationId) {

        MusicGenerationDTO.RevertResponse result = musicGenerationService.revertToVersion(userDetails, generationId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }

    // 원본(root) 곡의 유효한 버전 목록 조회
    @GetMapping("/{root_generation_id}/versions")
    @Override
    public ApiResponse<MusicGenerationDTO.VersionListResponse> getVersions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("root_generation_id") Long rootGenerationId) {

        MusicGenerationDTO.VersionListResponse result = musicGenerationService.getVersions(userDetails, rootGenerationId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }
}