package com.humix.api.domain.auth.controller;

import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthControllerDocs {

    @PostMapping("/guest-login")
    @Override
    public ApiResponse<Object> guestLogin(@RequestBody Object request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @PostMapping("/silent-refresh")
    @Override
    public ApiResponse<Object> silentRefresh() {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}