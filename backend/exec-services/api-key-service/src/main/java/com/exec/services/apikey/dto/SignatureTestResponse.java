package com.exec.services.apikey.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureTestResponse {

    /**
     * 생성된 Signature
     */
    private String signature;

    /**
     * Authorization 헤더 값 (Bearer 형식)
     * 예: Bearer ak_xxx:signature:timestamp
     */
    private String authorizationHeader;

    /**
     * 요청에 필요한 전체 헤더 맵
     */
    private Map<String, String> headers;

    /**
     * 서명 생성에 사용된 문자열 (디버깅용)
     */
    private String stringToSign;

    /**
     * 사용된 타임스탬프
     */
    private String timestamp;

    /**
     * 사용된 Access Key
     */
    private String accessKey;

    /**
     * 사용 예시 curl 명령어
     */
    private String curlExample;

    /**
     * 서명 검증 정보
     */
    private SignatureDebugInfo debugInfo;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignatureDebugInfo {
        private String method;
        private String uri;
        private String contentType;
        private String bodyHash;
        private String canonicalRequest;
        private String hmacAlgorithm;
    }
}