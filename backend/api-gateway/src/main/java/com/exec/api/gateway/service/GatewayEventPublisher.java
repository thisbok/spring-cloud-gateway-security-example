package com.exec.api.gateway.service;

import com.exec.api.gateway.event.ApiCallEvent;
import com.exec.api.gateway.event.AuthenticationEvent;
import com.exec.common.constants.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * API 호출 이벤트 발행 (비동기)
     */
    public Mono<Void> publishApiCallEvent(ApiCallEvent event) {
        return Mono.fromCallable(() -> {
                    try {
                        CompletableFuture<SendResult<String, Object>> future =
                                kafkaTemplate.send(KafkaTopics.API_CALL_EVENTS, event.getRequestId(), event);

                        future.whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish API call event: requestId={}", event.getRequestId(), ex);
                            } else {
                                log.debug("API call event published: requestId={}, partition={}",
                                        event.getRequestId(), result.getRecordMetadata().partition());
                            }
                        });

                        return null;
                    } catch (Exception e) {
                        log.error("Failed to send API call event: requestId={}", event.getRequestId(), e);
                        return null;
                    }
                }).subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 인증 이벤트 발행 (비동기)
     */
    public Mono<Void> publishAuthenticationEvent(AuthenticationEvent event) {
        return Mono.fromCallable(() -> {
                    try {
                        String key = event.getClientId() != null ? event.getClientId() : event.getAccessKey();
                        CompletableFuture<SendResult<String, Object>> future =
                                kafkaTemplate.send(KafkaTopics.AUTHENTICATION_EVENTS, key, event);

                        future.whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish authentication event: requestId={}, success={}",
                                        event.getRequestId(), event.isSuccess(), ex);
                            } else {
                                log.debug("Authentication event published: requestId={}, success={}, partition={}",
                                        event.getRequestId(), event.isSuccess(), result.getRecordMetadata().partition());
                            }
                        });

                        return null;
                    } catch (Exception e) {
                        log.error("Failed to send authentication event: requestId={}", event.getRequestId(), e);
                        return null;
                    }
                }).subscribeOn(Schedulers.boundedElastic())
                .then();
    }


    /**
     * 동기적 이벤트 발행 (테스트용)
     */
    public void publishApiCallEventSync(ApiCallEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.API_CALL_EVENTS, event.getRequestId(), event).get();
            log.debug("API call event published synchronously: requestId={}", event.getRequestId());
        } catch (Exception e) {
            log.error("Failed to send API call event synchronously: requestId={}", event.getRequestId(), e);
        }
    }

    /**
     * 여러 이벤트 배치 발행
     */
    public Mono<Void> publishEvents(ApiCallEvent apiCallEvent, AuthenticationEvent authEvent) {
        return Mono.when(
                publishApiCallEvent(apiCallEvent),
                publishAuthenticationEvent(authEvent)
        );
    }
}