package com.humix.api.domain.humming.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.humming.dto.HummingDTO;
import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.humming.repository.HummingRepository;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.melodyScore.repository.MelodyScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
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
    private final MelodyScoreRepository melodyScoreRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    //[POST] Humming ID 기반으로 원본 파일 경로를 참조해 FastAPI에 벡터화를 위임하고 결과를 영속화합니다.
    @Transactional
    public Page<HummingDTO.HummingSaveResponse> convertAndSaveVector(Long hummingId) {
        Humming humming = hummingRepository.findById(hummingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 허밍 기록이 존재하지 않습니다. ID: " + hummingId));

        WebClient webClient = webClientBuilder.baseUrl(aiServerUrl).build();

        Page<HummingDTO.HummingSaveResponse> result = webClient.post()
                .uri("/api/v1/ai/melody-extract")
                .bodyValue(Map.of("local_file_path", humming.getS3FileUrl())) // 파일 위치를 동봉해요.
                .retrieve()
                .bodyToMono(List.class) // 파이썬이 보내준 결과를 List(리스트) 형태로 변환해줘요.
                .block(); // 4. [중요] 파이썬이 수학 연산을 끝내고 대답을 줄 때까지 스레드가 멈춰서 기다려요(동기식 처리).

        try {
            // 5. 파이썬이 준 리스트 결과를 한 줄의 긴 글자(JSON 문자열)로 압축 가공해요.
            String rawJsonNotes = objectMapper.writeValueAsString(result.);

            // 6. 가공한 글자를 담아 새로운 음표 엔티티를 조립해요.
            MelodyScore melodyVector = MelodyScore.builder()
                    .humming(humming)
                    .notesData(rawJsonNotes)
                    .build();

            // 7. 데이터베이스 방에 최종 저장(영속화)해요.
            melodyScoreRepository.save(melodyVector);
        } catch (Exception e) {
            throw new IllegalStateException("멜로디 벡터 데이터 가공 및 저장에 실패했습니다.", e);
        }

        return result;
    }
}
