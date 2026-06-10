package com.humix.api.domain.musicGeneration.controller;

import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/generation/songs")
public class GenerationController implements GenerationControllerDocs {

    // ⑧ 노래 생성 요청 (비동기)
    @PostMapping("")
    @Override
    public ApiResponse<Object> generateSong(@RequestBody Object request) {
        // 비동기 요청이므로 202 ACCEPTED 반환 권장
        return ApiResponse.onSuccess(GeneralSuccessCode.ACCEPTED, null);
    }

    // ⑨ & ⑫ 진행 상황 구독 (SSE)
    @GetMapping(value = "/tasks/{task_id}/stream", produces = "text/event-stream")
    @Override
    public SseEmitter subscribeTaskStream(@PathVariable("task_id") String taskId) {
        // SSE 연결을 위한 Emitter 반환 (초기화 뼈대)
        SseEmitter emitter = new SseEmitter(1000 * 60 * 5L); // 5분 타임아웃
        return emitter;
    }

    // ⑩ 비동기 작업 취소
    @DeleteMapping("/tasks/{task_id}")
    @Override
    public ApiResponse<Object> cancelTask(@PathVariable("task_id") String taskId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    // ⑪ 생성곡 프롬프트 수정 요청
    @PostMapping("/{song_id}/modifications")
    @Override
    public ApiResponse<Object> modifySongPrompt(
            @PathVariable("song_id") Long songId,
            @RequestBody Object request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.ACCEPTED, null);
    }
}