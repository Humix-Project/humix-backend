package com.humix.api.domain.upload.controller;

import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload")
public class UploadController implements UploadControllerDocs {

    @PostMapping("/audio/presigned")
    @Override
    public ApiResponse<Object> getPresignedUrl(@RequestBody Object request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @PostMapping("/humming")
    @Override
    public ApiResponse<Object> saveHummingInfo(@RequestBody Object request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @PostMapping("/reference-tracks")
    @Override
    public ApiResponse<Object> saveReferenceTrackInfo(@RequestBody Object request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}