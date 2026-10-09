package com.humix.api.domain.member.controller;

import com.humix.api.domain.musicGeneration.dto.MusicGenerationDTO;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/me")
public interface MeControllerDocs {

    @Operation(summary = "내 생성곡 목록 페이징 조회 API", description = "사용자가 생성한 곡 목록을 장르 조건에 맞게 페이징하여 조회합니다.\n\n" +
            "- 완료(COMPLETED)된 곡만 조회되며, 버전 되돌리기로 삭제된 버전은 제외됩니다. (`total_count`, `total_pages`에도 반영)\n" +
            "- 같은 곡의 수정본도 버전마다 별도 항목으로 조회됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    })
    @GetMapping("/songs")
    ApiResponse<MusicGenerationDTO.SongListResponse> getMySongs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false) String genre);
}