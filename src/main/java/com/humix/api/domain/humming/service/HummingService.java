package com.humix.api.domain.humming.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.humming.entity.Humming;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HummingService {
    private final HummingRepository hummingRepository;
    private final MelodyVectorRepository melodyVectorRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    /**
     * [POST] Humming ID 기반으로 원본 파일 경로를 참조해 FastAPI에 벡터화를 위임하고 결과를 영속화합니다.
     */
    @Transactional
    public List<Map<String, Object>> convertAndSaveVector(Long hummingId) {
        Humming humming = hummingRepository.findById(hummingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 허밍 기록이 존재하지 않습니다. ID: " + hummingId));

        // 1. FastAPI AI 엔진으로 로컬/S3 파일의 key를 전달하여 주파수 연산 위임
        WebClient webClient = webClientBuilder.baseUrl(aiServerUrl).build();

        List<Map<String, Object>> aiApiResponse = webClient.post()
                .uri("/api/v1/ai/melody-extract")
                .bodyValue(Map.of("local_file_path", humming.getFileKey())) // AI 규격 바인딩
                .retrieve()
                .bodyToMono(List.class)
                .block(); // 비동기 응답 대기 및 동기 확보

        try {
            // 2. 전달받은 Note 시퀀스 JSON 배열을 TEXT 구조로 변환하여 DB 적재
            String rawJsonNotes = objectMapper.writeValueAsString(aiApiResponse);
            MelodyVector melodyVector = MelodyVector.builder()
                    .humming(humming)
                    .noteData(rawJsonNotes)
                    .build();

            melodyVectorRepository.save(melodyVector);
        } catch (Exception e) {
            throw new IllegalStateException("멜로디 벡터 데이터 가공 및 저장에 실패했습니다.", e);
        }

        return aiApiResponse;
    }

    /**
     * [PUT] 사용자가 웹 에디터 화면에서 수정한 멜로디 노드 상태값을 전달받아 갱신합니다.
     */
    @Transactional
    public List<Map<String, Object>> updateMelodyVector(Long hummingId, MelodyUpdateRequest request) {
        MelodyVector melodyVector = melodyVectorRepository.findById(hummingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 허밍의 추출된 멜로디 벡터가 존재하지 않습니다."));

        try {
            // 사용자가 드래그하여 수정한 음고/시간 노드 데이터를 새롭게 동기화
            String updatedJsonNotes = objectMapper.writeValueAsString(request.notes());

            // JPA 변경 감지(Dirty Checking)를 통한 엔티티 수정 반영
            melodyVectorRepository.save(
                    MelodyVector.builder()
                            .hummingId(melodyVector.getHummingId())
                            .humming(melodyVector.getHumming())
                            .noteData(updatedJsonNotes)
                            .build()
            );
        } catch (Exception e) {
            throw new IllegalStateException("멜로디 벡터 수정 과정에서 오류가 발생했습니다.", e);
        }

        return request.notes();
    }
}
