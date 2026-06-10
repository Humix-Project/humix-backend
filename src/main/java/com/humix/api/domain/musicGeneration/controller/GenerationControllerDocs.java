package com.humix.api.domain.musicGeneration.controller;

import com.humix.api.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequestMapping("/api/v1/generation/songs")
public interface GenerationControllerDocs {

    @Operation(summary = "노래 생성 요청 API (비동기)", description = "AI 엔진에 노래 편곡 및 생성을 비동기로 요청합니다. (202 Accepted 반환)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "작업 요청 수락됨")
    })
    @PostMapping("")
    ApiResponse<Object> generateSong(@RequestBody Object request);

    @Operation(summary = "노래 생성 진행 상황 구독 API (SSE)", description = "Task ID를 통해 비동기 작업 진행률과 완료 결과를 스트림으로 받습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스트림 연결 성공")
    })
    @GetMapping(value = "/tasks/{task_id}/stream", produces = "text/event-stream")
    SseEmitter subscribeTaskStream(@PathVariable("task_id") String taskId);

    @Operation(summary = "진행 중인 노래 생성 취소 API", description = "비동기 작곡 Task를 중단하고 상태를 CANCELED로 변경합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    })
    @DeleteMapping("/tasks/{task_id}")
    ApiResponse<Object> cancelTask(@PathVariable("task_id") String taskId);

    @Operation(summary = "생성곡 프롬프트 수정 요청 API", description = "기존 완성곡을 바탕으로 프롬프트를 주입하여 새로운 버전으로 수정을 요청합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "작업 요청 수락됨")
    })
    @PostMapping("/{song_id}/modifications")
    ApiResponse<Object> modifySongPrompt(
            @PathVariable("song_id") Long songId,
            @RequestBody Object request);
}