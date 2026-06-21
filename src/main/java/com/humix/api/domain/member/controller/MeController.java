package com.humix.api.domain.member.controller;

import com.humix.api.domain.musicGeneration.dto.MusicGenerationDTO;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import com.humix.api.domain.musicGeneration.repository.MusicGenerationRepository;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import com.humix.api.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class MeController implements MeControllerDocs {

    private final MusicGenerationRepository musicGenerationRepository;

    @GetMapping("/songs")
    @Override
    public ApiResponse<MusicGenerationDTO.SongListResponse> getMySongs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false) String genre) {

        // PageRequest is 0-indexed, but API page request is 1-indexed
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt")));
        Page<MusicGeneration> songPage;

        if (genre != null && !genre.isBlank()) {
            songPage = musicGenerationRepository.findByMemberAndGenreAndStatus(
                    userDetails.getMember(), genre, GenerationStatus.COMPLETED, pageable
            );
        } else {
            songPage = musicGenerationRepository.findByMemberAndStatus(
                    userDetails.getMember(), GenerationStatus.COMPLETED, pageable
            );
        }

        List<MusicGenerationDTO.SongItemResponse> songItems = songPage.getContent().stream()
                .map(song -> {
                    double hummingDuration = song.getMelodyScore().getHumming().getDurationSeconds();
                    int duration = song.getDurationSeconds() != null ? song.getDurationSeconds() : 30;
                    return MusicGenerationDTO.SongItemResponse.of(song, hummingDuration, duration);
                })
                .collect(Collectors.toList());

        MusicGenerationDTO.SongListResponse response = MusicGenerationDTO.SongListResponse.of(
                songPage.getTotalElements(),
                page,
                songPage.getTotalPages(),
                songItems
        );

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}