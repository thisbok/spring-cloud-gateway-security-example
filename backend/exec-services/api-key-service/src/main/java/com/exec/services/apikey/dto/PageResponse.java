package com.exec.services.apikey.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징 응답 래퍼 DTO
 * <p>
 * Spring Data Page 객체를 안정적인 JSON 구조로 변환
 * PageImpl 직렬화 경고 해결
 */
@Getter
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private PageInfo pageInfo;

    /**
     * Spring Data Page 를 PageResponse 로 변환
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                new PageInfo(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.isFirst(),
                        page.isLast(),
                        page.hasNext(),
                        page.hasPrevious()
                )
        );
    }

    /**
     * 페이징 메타데이터
     */
    @Getter
    @AllArgsConstructor
    public static class PageInfo {
        private int pageNumber;      // 현재 페이지 번호 (0 부터 시작)
        private int pageSize;         // 페이지 크기
        private long totalElements;   // 전체 요소 수
        private int totalPages;       // 전체 페이지 수
        private boolean first;        // 첫 페이지 여부
        private boolean last;         // 마지막 페이지 여부
        private boolean hasNext;      // 다음 페이지 존재 여부
        private boolean hasPrevious;  // 이전 페이지 존재 여부
    }
}
