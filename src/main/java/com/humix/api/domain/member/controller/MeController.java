package com.humix.api.domain.member.controller;

import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class MeController implements MeControllerDocs {

    @GetMapping("/songs")
    @Override
    public ApiResponse<Object> getMySongs(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false) String genre) {

        // 구현 시 return ApiResponse.onPageSuccess()의 형태로 변경
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}