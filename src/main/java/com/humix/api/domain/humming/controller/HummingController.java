package com.humix.api.domain.humming.controller;

import com.humix.api.domain.humming.service.HummingService;
import com.humix.api.domain.melodyScore.dto.MelodyScoreDTO;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/hummings")
public class HummingController implements HummingControllerDocs {

    private final HummingService hummingService;

    @PostMapping("/{humming_id}/vectors")
    @Override
    public ApiResponse<MelodyScoreDTO.MelodyVectorResponse> convertHummingToVector(@PathVariable("humming_id") Long hummingId) {

        MelodyScoreDTO.MelodyVectorResponse result = hummingService.convertHummingToVector(hummingId);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }

    @PutMapping("/{humming_id}/vectors")
    @Override
    public ApiResponse<MelodyScoreDTO.MelodyVectorResponse> updateHummingVector(
            @PathVariable("humming_id") Long hummingId,
            @RequestBody MelodyScoreDTO.MelodyUpdateRequest request) {

         MelodyScoreDTO.MelodyVectorResponse result = hummingService.updateHummingVector(hummingId, request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }
}