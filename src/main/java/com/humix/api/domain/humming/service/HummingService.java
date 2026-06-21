package com.humix.api.domain.humming.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.humming.repository.HummingRepository;
import com.humix.api.domain.melodyScore.dto.MelodyScoreDTO;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.melodyScore.repository.MelodyScoreRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
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
    public MelodyScoreDTO.MelodyVectorResponse convertHummingToVector(Long hummingId) {
        // 1. 데이터베이스로부터 허밍 엔티티 및 적재된 S3 오디오 주소를 로드합니다.
        Humming humming = hummingRepository.findById(hummingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 허밍 기록이 존재하지 않습니다. ID: " + hummingId));

        String s3Url = humming.getS3FileUrl();

        validateS3Url(s3Url);

        // 2. FastAPI 통신 파이프라인 개방
        WebClient webClient = webClientBuilder.baseUrl(aiServerUrl).build();

        List<?> rawResponse = webClient.post()
                .uri("/api/v1/ai/melody-extract")
                .bodyValue(Map.of("s3_url", humming.getS3FileUrl()))
                .retrieve()
                .bodyToMono(List.class)
                .block();

        try {
            // 2. objectMapper와 TypeReference를 사용하여 명확한 타입으로 안전하게 변환합니다.
            List<Map<String, Object>> aiServerResponse = objectMapper.convertValue(
                    rawResponse,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            // 3. 데이터를 기반으로 DTO 매핑을 진행합니다.
            List<MelodyScoreDTO.NoteDto> rawNotes = objectMapper.convertValue(
                    aiServerResponse,
                    new TypeReference<List<MelodyScoreDTO.NoteDto>>() {}
            );

            // 4. start_time_seconds(음표 누적 시작 시점) 계산
            List<MelodyScoreDTO.NoteDto> finalizedNotes = calculateStartTimes(rawNotes);

            // 5. 악보 데이터(JSON 문자열)를 MelodyScore 엔티티에 세팅
            String jsonNotesData = objectMapper.writeValueAsString(finalizedNotes);
            MelodyScore melodyScore = MelodyScore.builder()
                    .humming(humming)
                    .notesData(jsonNotesData)
                    .build();

            melodyScoreRepository.save(melodyScore);

            // 6. 저장된 데이터를 DTO Response 리턴
            return MelodyScoreDTO.MelodyVectorResponse.from(melodyScore);

        } catch (Exception e) {
            throw new IllegalStateException("멜로디 벡터화 과정에서 오류가 발생했습니다.", e);
        }
    }

    private void validateS3Url(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("DB에 저장된 S3 URL이 비어있습니다.");
        }
        try {
            // 자바 내장 URI 클래스로 파싱을 시도하여 구조적 결함(https:/// 등)을 1차로 걸러냅니다.
            java.net.URI uri = java.net.URI.create(urlString.trim());

            if (uri.getHost() == null || !uri.getScheme().equalsIgnoreCase("https")) {
                throw new IllegalArgumentException("잘못된 형식의 S3 호스트 주소입니다: " + urlString);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("S3 URL 문자열 파싱 중 Bad Authority 구조적 결함 발견: " + urlString, e);
        }
    }

    private static @NonNull List<MelodyScoreDTO.NoteDto> calculateStartTimes(List<MelodyScoreDTO.NoteDto> rawNotes) {
        double currentStartTime = 0.0;
        List<MelodyScoreDTO.NoteDto> finalizedNotes = new ArrayList<>();

        for (MelodyScoreDTO.NoteDto rawNote : rawNotes) {
            // 시작 시간 정보를 누적 조립하여 완성형 NoteDto 객체 체인을 빌드
            MelodyScoreDTO.NoteDto orderedNote = new MelodyScoreDTO.NoteDto(
                    Math.round(currentStartTime * 10.0) / 10.0, // 부동소수점 오차 방지 보정
                    rawNote.pitch(),
                    rawNote.durationSeconds()
            );
            finalizedNotes.add(orderedNote);

            // 다음 음표의 시작 시점 = 현재 음표의 시작 시점 + 현재 음표의 음길이
            currentStartTime += rawNote.durationSeconds();
        }
        return finalizedNotes;
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
