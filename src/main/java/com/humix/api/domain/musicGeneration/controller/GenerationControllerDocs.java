package com.humix.api.domain.musicGeneration.controller;

import com.humix.api.domain.musicGeneration.dto.MusicGenerationDTO;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequestMapping("/api/v1/generation/songs")
public interface GenerationControllerDocs {

    @Operation(
            summary = "노래 생성 요청 API (비동기)",
            description = "AI 엔진에 노래 편곡 및 생성을 비동기로 요청합니다. (202 Accepted 반환)\n\n" +
                    "- `humming_id`: 벡터화가 완료된 허밍의 ID (필수)\n" +
                    "- `title`: 생성할 곡 제목 (생략 시 기본값 '나의 허밍곡' 사용)\n" +
                    "- `genre`: 원하는 장르 (예: pop, jazz, classical, hiphop)\n" +
                    "- `mood`: 원하는 분위기 (예: upbeat, calm, romantic, sad)\n" +
                    "- `reference_track_id`: 참조할 트랙 ID (선택사항, 없으면 null)")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MusicGenerationDTO.SongCreateRequest.class),
                    examples = @ExampleObject(
                            name = "노래 생성 요청 예시",
                            value = "{\n" +
                                    "  \"humming_id\": 1,\n" +
                                    "  \"title\": \"나의 첫 번째 곡\",\n" +
                                    "  \"genre\": \"pop\",\n" +
                                    "  \"mood\": \"upbeat\",\n" +
                                    "  \"reference_track_id\": 3\n" +
                                    "}"
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "작업 요청 수락됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "202 Accepted",
                                    value = "{\n" +
                                            "  \"isSuccess\": true,\n" +
                                            "  \"code\": \"COMMON202\",\n" +
                                            "  \"message\": \"요청이 수락되었습니다.\",\n" +
                                            "  \"result\": {\n" +
                                            "    \"task_id\": \"task_550e8400-e29b-41d4-a716-446655440000\"\n" +
                                            "  }\n" +
                                            "}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"isSuccess\": false, \"code\": \"AUTH4000\", \"message\": \"로그인이 필요한 기능입니다.\", \"result\": null}"
                            )
                    )
            )
    })
    @PostMapping("")
    ApiResponse<MusicGenerationDTO.TaskAcceptedResponse> generateSong(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestBody MusicGenerationDTO.SongCreateRequest request);

    @Operation(
            summary = "노래 생성 진행 상황 구독 API (SSE)",
            description = "Task ID를 통해 비동기 작업 진행률과 완료 결과를 Server-Sent Events(SSE) 스트림으로 받습니다.\n\n" +
                    "**이벤트 종류:**\n" +
                    "- `connect`: 최초 연결 성공 시 전송\n" +
                    "- `progress`: 진행률 업데이트 (status: PROCESSING, progress: 0~90)\n" +
                    "- `complete`: 작업 완료 시 전송 (status: COMPLETED / FAILED)\n\n" +
                    "**progress 이벤트 예시:** `{\"task_id\": \"task_xxx\", \"status\": \"PROCESSING\", \"progress\": 50}`\n\n" +
                    "**complete 이벤트 예시:** `{\"status\": \"COMPLETED\", \"result\": {\"task_id\": \"task_xxx\", \"song_id\": 42, \"audio_url\": \"https://...\", \"duration_seconds\": 30}}`"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스트림 연결 성공")
    })
    @GetMapping(value = "/tasks/{task_id}/stream", produces = "text/event-stream")
    SseEmitter subscribeTaskStream(@PathVariable("task_id") String taskId);

    @Operation(
            summary = "진행 중인 노래 생성 취소 API",
            description = "비동기 작곡 Task를 중단하고 상태를 CANCELED로 변경합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "취소 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"isSuccess\": true,\n" +
                                            "  \"code\": \"COMMON200\",\n" +
                                            "  \"message\": \"성공입니다.\",\n" +
                                            "  \"result\": {\n" +
                                            "    \"task_id\": \"task_550e8400-e29b-41d4-a716-446655440000\",\n" +
                                            "    \"status\": \"CANCELED\"\n" +
                                            "  }\n" +
                                            "}"
                            )
                    )
            )
    })
    @DeleteMapping("/tasks/{task_id}")
    ApiResponse<MusicGenerationDTO.TaskCancelResponse> cancelTask(@PathVariable("task_id") String taskId);

    @Operation(
            summary = "생성곡 프롬프트 수정 요청 API",
            description = "기존 완성곡을 바탕으로 프롬프트를 주입하여 새로운 버전으로 수정을 비동기 요청합니다. (202 Accepted 반환)\n\n" +
                    "- `song_id` (path): 수정 대상 원본 완성곡의 ID\n" +
                    "- `prompt`: 수정 방향 자연어 설명 (예: '더 밝고 경쾌하게', '템포를 느리게')"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MusicGenerationDTO.SongModificationRequest.class),
                    examples = @ExampleObject(
                            name = "수정 요청 예시",
                            value = "{\n" +
                                    "  \"prompt\": \"더 밝고 경쾌하게 수정해줘\"\n" +
                                    "}"
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "작업 요청 수락됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"isSuccess\": true,\n" +
                                            "  \"code\": \"COMMON202\",\n" +
                                            "  \"message\": \"요청이 수락되었습니다.\",\n" +
                                            "  \"result\": {\n" +
                                            "    \"task_id\": \"task_a1b2c3d4-e5f6-7890-abcd-ef1234567890\"\n" +
                                            "  }\n" +
                                            "}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"isSuccess\": false, \"code\": \"AUTH4000\", \"message\": \"로그인이 필요한 기능입니다.\", \"result\": null}"
                            )
                    )
            )
    })
    @PostMapping("/{song_id}/modifications")
    ApiResponse<MusicGenerationDTO.TaskAcceptedResponse> modifySongPrompt(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("song_id") Long songId,
            @org.springframework.web.bind.annotation.RequestBody MusicGenerationDTO.SongModificationRequest request);
}