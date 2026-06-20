package com.humix.api.domain.humming.controller;

import com.humix.api.domain.humming.service.HummingService;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/hummings")
public class HummingController implements HummingControllerDocs {

    private final HummingService hummingService;

    @PostMapping("/{humming_id}/vectors")
    @Override
    public ApiResponse<Object> convertHummingToVector(@PathVariable("humming_id") Long hummingId) {

        List<Map<String, Object>> extractedVector = hummingService.convertAndSaveVector(hummingId);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @PutMapping("/{humming_id}/vectors")
    @Override
    public ApiResponse<Object> updateHummingVector(
            @PathVariable("humming_id") Long hummingId,
            @RequestBody Object request) {

        List<Map<String, Object>> updatedVector = hummingService.updateMelodyVector(hummingId, request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}