package com.exec.services.analytics.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Elasticsearch 인덱스 템플릿 및 네이밍 관리
 * <p>
 * 1. 애플리케이션 시작 시 인덱스 템플릿 생성
 * 2. 템플릿에 매칭되는 인덱스 (api-gateway-logs-*) 가 생성될 때 자동으로 매핑 적용
 * 3. 일별 인덱스 이름 생성 유틸리티 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexConfig {

    private static final String TEMPLATE_NAME = "api-gateway-logs-template";
    private static final String INDEX_PREFIX = "api-gateway-logs-";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private final ElasticsearchClient elasticsearchClient;

    /**
     * 애플리케이션 시작 시 인덱스 템플릿 생성
     * <p>
     * 템플릿이 생성되면 이후 api-gateway-logs-* 패턴의 인덱스가 생성될 때
     * 자동으로 템플릿의 매핑과 설정이 적용됩니다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndexTemplate() {
        try {
            log.info("Creating Elasticsearch index template: {}", TEMPLATE_NAME);
            createIndexTemplate();
            log.info("Index template created successfully. Pattern: api-gateway-logs-*");
        } catch (Exception e) {
            log.error("Failed to create index template", e);
        }
    }

    /**
     * 인덱스 템플릿 생성
     */
    private void createIndexTemplate() throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "elasticsearch/mappings/api-gateway-request-log-mapping.json");

        try (InputStream inputStream = resource.getInputStream()) {
            String mappingJson = new String(inputStream.readAllBytes());
            Reader jsonReader = new StringReader(mappingJson);

            PutIndexTemplateRequest request = PutIndexTemplateRequest.of(t -> t
                    .name(TEMPLATE_NAME)
                    .withJson(jsonReader)
            );

            elasticsearchClient.indices().putIndexTemplate(request);
            log.debug("Template JSON applied: {}", TEMPLATE_NAME);
        }
    }


    /**
     * 오늘 날짜의 인덱스명 생성
     */
    public String getTodayIndexName() {
        return getIndexNameForDate(LocalDate.now());
    }

    /**
     * 특정 날짜의 인덱스명 생성
     */
    public String getIndexNameForDate(LocalDate date) {
        return INDEX_PREFIX + date.format(DATE_FORMATTER);
    }

    /**
     * 인덱스 패턴 반환 (검색용)
     */
    public String getIndexPattern() {
        return INDEX_PREFIX + "*";
    }
}
