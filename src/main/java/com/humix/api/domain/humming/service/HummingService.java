package com.humix.api.domain.humming.service;

import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.humming.repository.HummingRepository;
import com.humix.api.domain.melodyScore.dto.MelodyScoreDTO;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.melodyScore.repository.MelodyScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HummingService {
    private final HummingRepository hummingRepository;
    private final MelodyScoreRepository melodyScoreRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    //[POST] Humming ID 기반으로 원본 파일 경로를 참조해 FastAPI에 벡터화를 위임하고 결과를 영속화합니다.
    @Transactional
    public MelodyScoreDTO.MelodyVectorResponse convertHummingToVector(Long hummingId) {
        Humming humming = hummingRepository.findById(hummingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 허밍 기록이 존재하지 않습니다. ID: " + hummingId));

        return result;
    }

    @Transactional
    public MelodyScoreDTO.MelodyVectorResponse updateHummingVector(Long hummingId, MelodyScoreDTO.MelodyUpdateRequest request){

        MelodyScoreDTO.MelodyVectorResponse result = null;

        return result;
    }
}
