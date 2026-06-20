package com.humix.api.domain.upload.controller;

import com.humix.api.domain.humming.dto.HummingDTO;
import com.humix.api.domain.upload.dto.UploadDTO;
import com.humix.api.domain.upload.service.UploadService;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload")
public class UploadController implements UploadControllerDocs {

    private final UploadService uploadService;

    @PostMapping("/audio/presigned")
    @Override
    public ApiResponse<UploadDTO.AudioPresignedResponse> getPresignedUrl(@RequestBody UploadDTO.AudioPresignedRequest request) {

        UploadDTO.AudioPresignedResponse result = uploadService.getAudioPresignedUrl(request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }

    @PostMapping("/humming")
    @Override
    public ApiResponse<HummingDTO.HummingSaveResponse> saveHummingInfo(@RequestBody HummingDTO.HummingSaveRequest request) {

        HummingDTO.HummingSaveResponse result = uploadService.saveHummingMetadata(request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, result);
    }

    @PostMapping("/reference-tracks")
    @Override
    public ApiResponse<Object> saveReferenceTrackInfo(@RequestBody Object request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}