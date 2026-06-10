package com.humix.api.domain.humming.controller;

import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/hummings")
public class HummingController implements HummingControllerDocs {

    @PostMapping("/{humming_id}/vectors")
    @Override
    public ApiResponse<Object> convertHummingToVector(@PathVariable("humming_id") Long hummingId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @PutMapping("/{humming_id}/vectors")
    @Override
    public ApiResponse<Object> updateHummingVector(
            @PathVariable("humming_id") Long hummingId,
            @RequestBody Object request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}