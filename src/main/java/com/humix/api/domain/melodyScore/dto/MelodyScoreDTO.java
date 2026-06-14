package com.humix.api.domain.melodyScore.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import java.util.List;

public class MelodyScoreDTO {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    //악보를 구성하는 개별 노트 정보
    public record NoteDto(
            @JsonProperty("start_time_seconds") double startTimeSeconds,
            int pitch,
            @JsonProperty("duration_seconds") double durationSeconds
    ) {}

    //멜로디 벡터 수정 요청 Request Body
    public record MelodyUpdateRequest(
            @JsonProperty("humming_id") Long hummingId,
            List<NoteDto> notes
    ) {
        public MelodyScore from(Humming humming) {

            try {
                String jsonNotesData = objectMapper.writeValueAsString(this.notes);
                return MelodyScore.builder()
                        .humming(humming)
                        .notesData(jsonNotesData)
                        .build();
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("노트 데이터를 JSON 문자열로 변환하는데 실패했습니다.", e);
            }
        }
    }

    //멜로디 벡터 응답 Response Body
    public record MelodyVectorResponse(
            @JsonProperty("humming_id") Long hummingId,
            List<NoteDto> notes
    ) {
        //MelodyScore 엔티티를 받아 응답 DTO로 변환하는 메소드
        public static MelodyVectorResponse from(MelodyScore melodyScore) {
            try {
                List<NoteDto> parsedNotes = objectMapper.readValue(
                        melodyScore.getNotesData(),
                        new TypeReference<List<NoteDto>>() {}
                );

                return new MelodyVectorResponse(
                        melodyScore.getHumming().getId(),
                        parsedNotes
                );
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("DB의 악보 JSON 데이터를 객체로 파싱하는데 실패했습니다.", e);
            }
        }
    }
}
