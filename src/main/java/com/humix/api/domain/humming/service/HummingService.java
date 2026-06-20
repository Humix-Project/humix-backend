package com.humix.api.domain.humming.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static com.humix.api.domain.melodyScore.dto.MelodyScoreDTO.objectMapper;

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
    public MelodyScoreDTO.MelodyVectorResponse convertHummingToVector(Long hummingId) {
        Humming humming = hummingRepository.findById(hummingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 허밍 기록이 존재하지 않습니다. ID: " + hummingId));

        return result;
    }

    @Transactional
    public MelodyScoreDTO.MelodyVectorResponse updateHummingVector(Long hummingId, MelodyScoreDTO.MelodyUpdateRequest request){

        // 1. 기존 데이터베이스에 존재하는 멜로디 악보 레코드를 식별 조회합니다.
        MelodyScore existingMelodyScore = melodyScoreRepository.findByHummingId(hummingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 허밍의 추출된 멜로디 벡터(MelodyScore)가 존재하지 않습니다. ID: " + hummingId));

        if (request == null || request.notes() == null) {
            throw new IllegalArgumentException("수정할 노트(notes) 데이터가 비어있습니다.");
        }

        try {
            // 2. 프론트에서 넘어온 수정 노트 리스트를 JSON 문자열로 가공합니다.
            String updatedJsonData = objectMapper.writeValueAsString(request.notes());

            // 3. 엔티티 내부의 도메인 편의 메서드를 호출하여 상태를 변경합니다.
            existingMelodyScore.updateNotesData(updatedJsonData);

            // 4. 상태가 동기화된 객체를 그대로 정적 팩토리 메서드에 바인딩하여 결과를 반환합니다.
            return MelodyScoreDTO.MelodyVectorResponse.from(existingMelodyScore);

        } catch (Exception e) {
            throw new IllegalStateException("웹 에디터 변경 벡터 데이터 갱신 과정에서 오류가 발생했습니다.", e);
        }
    }
}
