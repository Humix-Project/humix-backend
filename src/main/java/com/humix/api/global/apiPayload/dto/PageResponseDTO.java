package com.humix.api.global.apiPayload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class PageResponseDTO<T> {
    private final Long totalCount;
    private final Integer currentPage;
    private final Integer totalPages;
    private final Integer currentCount;
    private final List<T> content;

    public PageResponseDTO(Page<T> page) {
        this.totalCount = page.getTotalElements();
        // JPA의 Page는 0-index 기반이므로, 클라이언트에게는 1부터 줄 수 있도록
        this.currentPage = page.getNumber() + 1;
        this.totalPages = page.getTotalPages();
        this.currentCount = page.getNumberOfElements();
        this.content = page.getContent();
    }
}