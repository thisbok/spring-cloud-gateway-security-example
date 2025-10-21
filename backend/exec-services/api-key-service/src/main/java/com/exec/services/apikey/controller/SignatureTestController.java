package com.exec.services.apikey.controller;

import com.exec.services.apikey.dto.SignatureTestRequest;
import com.exec.services.apikey.dto.SignatureTestResponse;
import com.exec.services.apikey.service.SignatureTestService;
import com.exec.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test/signature")
@RequiredArgsConstructor
@Slf4j
public class SignatureTestController {

    private final SignatureTestService signatureTestService;

    /**
     * 테스트용 Signature 생성 및 Authorization 헤더 값 생성
     *
     * @param request 요청 데이터 (accessKey, secretKey, method, uri, requestBody, timestamp)
     * @return Signature 및 Authorization 헤더 값
     */
    @PostMapping("/generate")
    public ApiResponse<SignatureTestResponse> generateSignature(
            @Valid @RequestBody SignatureTestRequest request) {

        log.info("Generating test signature for accessKey: {}", request.getAccessKey());

        SignatureTestResponse response = signatureTestService.generateSignature(request);

        log.info("Test signature generated successfully");
        return ApiResponse.success(response);
    }

    /**
     * 저장된 API Key 를 사용한 Signature 생성
     *
     * @param accessKey API Access Key
     * @param request   요청 데이터 (method, uri, requestBody, timestamp)
     * @return Signature 및 Authorization 헤더 값
     */
    @PostMapping("/generate/{accessKey}")
    public ApiResponse<SignatureTestResponse> generateSignatureWithStoredKey(
            @PathVariable String accessKey,
            @Valid @RequestBody SignatureTestRequest request) {

        log.info("Generating test signature with stored key: {}", accessKey);

        SignatureTestResponse response = signatureTestService.generateSignatureWithStoredKey(accessKey, request);

        log.info("Test signature generated successfully with stored key");
        return ApiResponse.success(response);
    }
}