package com.humix.api.domain.upload.controller;

import com.humix.api.domain.humming.dto.HummingDTO;
import com.humix.api.domain.upload.dto.UploadDTO;
import com.humix.api.domain.upload.service.UploadService;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import com.humix.api.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload")
public class UploadController implements UploadControllerDocs {

    private final UploadService uploadService;

    @PostMapping("/audio/presigned")
    @Override
    public ApiResponse<UploadDTO.AudioPresignedResponse> getPresignedUrl(@RequestBody UploadDTO.AudioPresignedRequest request) {

        UploadDTO.AudioPresignedResponse result = uploadService.getPresignedUrl(request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }

    @PostMapping("/humming")
    @Override
    public ApiResponse<HummingDTO.HummingSaveResponse> saveHummingInfo(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                       @RequestBody HummingDTO.HummingSaveRequest request) {

        HummingDTO.HummingSaveResponse result = uploadService.saveHummingInfo(userDetails, request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }

    @PostMapping("/reference-tracks")
    @Override
    public ApiResponse<Object> saveReferenceTrackInfo(@RequestBody Object request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}