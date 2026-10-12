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
                    "- `prompt`: 원하는 스타일을 직접 서술하는 문장 (선택, 최대 500자)\n\n" +
                    "곡 길이는 서버 설정값(기본 30초)으로 고정되며, 멜로디가 이 길이를 넘으면 `HUMMING4001`(400)로 거부됩니다.")
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
                                    "  \"prompt\": \"잔잔한 피아노 위주로, 후반부에 스트링이 들어오는 느낌\"\n" +
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 허밍 길이 초과",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "검증 실패",
                                            value = "{\"isSuccess\":false, \"code\":\"COMMON400\", \"message\":\"잘못된 요청입니다.\", \"result\":{\"humming_id\":\"humming_id는 필수입니다.\"}}"),
                                    @ExampleObject(name = "허밍 길이 초과",
                                            value = "{\"isSuccess\":false, \"code\":\"HUMMING4001\", \"message\":\"허밍은 최대 30초까지 가능합니다.\", \"result\":null}")
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "멜로디 악보 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "악보 없음",
                                    value = "{\"isSuccess\":false, \"code\":\"MELODY4000\", \"message\":\"해당 허밍의 멜로디 악보가 존재하지 않습니다.\", \"result\":null}")))
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
                    "**complete 이벤트 예시:** `{\"status\": \"COMPLETED\", \"result\": {\"task_id\": \"task_xxx\", \"song_id\": 42, \"audio_url\": \"https://...\", \"duration_seconds\": 30.0}}`"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스트림 연결 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "태스크 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "태스크 없음",
                                    value = "{\"isSuccess\":false, \"code\":\"TASK4000\", \"message\":\"해당 태스크 ID의 작업 정보가 존재하지 않습니다.\", \"result\":null}")))
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "태스크 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "태스크 없음",
                                    value = "{\"isSuccess\":false, \"code\":\"TASK4000\", \"message\":\"해당 태스크 ID의 작업 정보가 존재하지 않습니다.\", \"result\":null}")))
    })
    @DeleteMapping("/tasks/{task_id}")
    ApiResponse<MusicGenerationDTO.TaskCancelResponse> cancelTask(@PathVariable("task_id") String taskId);

    @Operation(
            summary = "생성곡 구간 재생성 요청 API (비동기)",
            description = "선택한 구간만 다시 생성한 **새 버전**을 비동기로 요청합니다. (202 Accepted 반환)\n\n" +
                    "원곡(수정 대상 버전)은 그대로 유지되고 새 `generation_id`가 부여됩니다. 구간 자르기·문맥 선택·크로스페이드·병합은 AI 서버가 처리하며, " +
                    "완료되면 구간 밖 부분은 원곡을 유지한 **전체 곡**이 결과로 저장됩니다. 구간 끝 이후 약 1초는 크로스페이드로 원곡 소리가 일부 바뀔 수 있습니다.\n\n" +
                    "- `generation_id` (path): 수정 대상 버전의 ID (원본 또는 이전 수정본). 완료(COMPLETED)된 본인 곡이어야 합니다.\n" +
                    "- `prompt`: 재생성 구간에 적용할 스타일 프롬프트 (선택, 최대 500자). 생략하면 수정 대상 버전의 프롬프트를 그대로 사용합니다.\n" +
                    "- `start_time_sec`, `end_time_sec`: 재생성할 구간 (초, 수정 대상 버전 기준). `0 ≤ start`, `end - start ≥ 5`, `end ≤ 곡 길이`이며 곡 전체도 재생성할 수 있습니다.\n\n" +
                    "진행 상황은 응답의 `task_id`로 SSE(`/tasks/{task_id}/stream`)를 구독해 받습니다. " +
                    "AI 서버가 실패하거나 결과 길이가 원곡과 0.1초 넘게 다르면 새 버전은 만들어지지 않고(`FAILED`) 원곡이 유지됩니다."
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MusicGenerationDTO.SongModificationRequest.class),
                    examples = @ExampleObject(
                            name = "구간 재생성 요청 예시",
                            value = "{\n" +
                                    "  \"prompt\": \"더 밝고 경쾌하게\",\n" +
                                    "  \"start_time_sec\": 10,\n" +
                                    "  \"end_time_sec\": 20\n" +
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 잘못된 구간",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "검증 실패",
                                            value = "{\"isSuccess\":false, \"code\":\"COMMON400\", \"message\":\"잘못된 요청입니다.\", \"result\":{\"start_time_sec\":\"start_time_sec는 필수입니다.\"}}"),
                                    @ExampleObject(name = "잘못된 구간",
                                            value = "{\"isSuccess\":false, \"code\":\"GENERATION4002\", \"message\":\"재생성 구간이 올바르지 않습니다. 시작은 0초 이상, 구간 길이는 5초 이상이어야 하며 끝은 곡 길이를 넘을 수 없습니다.\", \"result\":null}")
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "수정 대상 곡 없음 (없는 ID, 삭제된 버전, 미완료 버전, 타인의 곡)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "원본 곡 없음",
                                    value = "{\"isSuccess\":false, \"code\":\"GENERATION4000\", \"message\":\"해당하는 생성곡이 존재하지 않습니다.\", \"result\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "같은 곡에 진행 중인 작업이 있음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "진행 중인 작업 있음",
                                    value = "{\"isSuccess\":false, \"code\":\"GENERATION4001\", \"message\":\"같은 곡의 다른 버전을 생성 중입니다. 완료되거나 취소된 후 다시 시도해주세요.\", \"result\":null}")))
    })
    @PostMapping("/{generation_id}/modifications")
    ApiResponse<MusicGenerationDTO.TaskAcceptedResponse> modifySongSection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("generation_id") Long generationId,
            @org.springframework.web.bind.annotation.RequestBody MusicGenerationDTO.SongModificationRequest request);

    @Operation(
            summary = "버전 되돌리기 API",
            description = "지정한 버전으로 되돌립니다. 같은 곡(root)에서 해당 버전보다 **뒤에 만들어진 버전은 모두 삭제(soft delete)** 되어 " +
                    "버전 목록과 내 곡 목록에서 사라집니다. (예: 버전 2로 되돌리면 3, 4 삭제)\n\n" +
                    "- `generation_id` (path): 되돌릴 기준 버전의 ID. 완료(COMPLETED)된 본인 곡이어야 합니다.\n" +
                    "- 같은 곡에 진행 중(PROCESSING)인 작업이 있으면 409를 반환합니다. 완료되거나 취소된 뒤 다시 시도하세요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "되돌리기 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"isSuccess\": true,\n" +
                                            "  \"code\": \"COMMON200\",\n" +
                                            "  \"message\": \"성공입니다.\",\n" +
                                            "  \"result\": {\n" +
                                            "    \"generation_id\": 42,\n" +
                                            "    \"deleted_count\": 2\n" +
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "되돌릴 수 있는 곡 없음 (없는 ID, 이미 삭제됨, 미완료, 타인의 곡)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "곡 없음",
                                    value = "{\"isSuccess\":false, \"code\":\"GENERATION4000\", \"message\":\"해당하는 생성곡이 존재하지 않습니다.\", \"result\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "같은 곡에 진행 중인 작업이 있음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "진행 중인 작업 있음",
                                    value = "{\"isSuccess\":false, \"code\":\"GENERATION4001\", \"message\":\"같은 곡의 다른 버전을 생성 중입니다. 완료되거나 취소된 후 다시 시도해주세요.\", \"result\":null}")))
    })
    @PostMapping("/{generation_id}/revert")
    ApiResponse<MusicGenerationDTO.RevertResponse> revertToVersion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("generation_id") Long generationId);

    @Operation(
            summary = "버전 목록 조회 API",
            description = "같은 곡의 유효한 버전(완료 + 삭제되지 않음)을 생성 순서(오래된 순)로 반환합니다.\n\n" +
                    "- `root_generation_id` (path): 원본(최초 생성곡)의 ID. 수정본의 ID가 아니라 원본의 ID를 넘겨야 합니다.\n" +
                    "- `version_no`: 목록 순서로 계산한 버전 번호 (1부터). 실패/취소된 작업은 번호를 차지하지 않습니다.\n" +
                    "- `start_time_sec`, `end_time_sec`: 해당 버전에서 다시 만든 구간(직전 버전 기준). 원본(최초 생성곡)은 null\n" +
                    "- 본인 곡만 조회할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"isSuccess\": true,\n" +
                                            "  \"code\": \"COMMON200\",\n" +
                                            "  \"message\": \"성공입니다.\",\n" +
                                            "  \"result\": {\n" +
                                            "    \"root_generation_id\": 40,\n" +
                                            "    \"versions\": [\n" +
                                            "      {\n" +
                                            "        \"generation_id\": 40,\n" +
                                            "        \"version_no\": 1,\n" +
                                            "        \"prompt\": null,\n" +
                                            "        \"start_time_sec\": null,\n" +
                                            "        \"end_time_sec\": null,\n" +
                                            "        \"audio_url\": \"https://humix-bucket.s3.amazonaws.com/audio/abc123_variation.wav\",\n" +
                                            "        \"duration_seconds\": 30.0,\n" +
                                            "        \"created_at\": \"2026-06-21T18:00:00\"\n" +
                                            "      },\n" +
                                            "      {\n" +
                                            "        \"generation_id\": 42,\n" +
                                            "        \"version_no\": 2,\n" +
                                            "        \"prompt\": \"더 밝고 경쾌하게\",\n" +
                                            "        \"start_time_sec\": 10,\n" +
                                            "        \"end_time_sec\": 20,\n" +
                                            "        \"audio_url\": \"https://humix-bucket.s3.amazonaws.com/audio/def456_variation.wav\",\n" +
                                            "        \"duration_seconds\": 30.0,\n" +
                                            "        \"created_at\": \"2026-06-21T18:05:00\"\n" +
                                            "      }\n" +
                                            "    ]\n" +
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "곡 없음 (없는 ID, 삭제됨, 타인의 곡, 원본이 아닌 수정본의 ID)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "곡 없음",
                                    value = "{\"isSuccess\":false, \"code\":\"GENERATION4000\", \"message\":\"해당하는 생성곡이 존재하지 않습니다.\", \"result\":null}")))
    })
    @GetMapping("/{root_generation_id}/versions")
    ApiResponse<MusicGenerationDTO.VersionListResponse> getVersions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("root_generation_id") Long rootGenerationId);
}