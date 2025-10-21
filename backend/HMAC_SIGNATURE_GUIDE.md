# HMAC 서명 생성 및 검증 가이드

## 목차

1. [개요](#개요)
2. [HMAC 서명이란](#hmac-서명이란)
3. [서명 데이터 구조](#서명-데이터-구조)
4. [서명 생성 프로세스](#서명-생성-프로세스)
5. [서명 검증 프로세스](#서명-검증-프로세스)
6. [지원 알고리즘](#지원-알고리즘)
7. [Query String 정규화](#query-string-정규화)
8. [Body Hash 계산](#body-hash-계산)
9. [Replay Attack 방지](#replay-attack-방지)
10. [구현 예시](#구현-예시)
11. [보안 고려사항](#보안-고려사항)
12. [성능 최적화](#성능-최적화)
13. [문제 해결](#문제-해결)

---

## 개요

본 시스템은 **HMAC (Hash-based Message Authentication Code)** 방식을 사용하여 API 요청의 무결성과 인증을 보장합니다. 클라이언트와 서버가 공유하는 Secret Key 를 사용하여 요청 데이터에 서명하고, 서버에서 이를 검증합니다.

### 주요 목적

- **무결성 검증**: 요청이 전송 중 변조되지 않았음을 보장
- **인증**: 유효한 Secret Key 를 가진 클라이언트만 요청 가능
- **Replay Attack 방지**: Timestamp 와 Idempotency Key 로 재전송 공격 차단
- **비부인**: 서명된 요청은 부인할 수 없음

### 보안 모델

```
Client (Access Key + Secret Key)
    ↓
1. Request 데이터 구성 (Method, Path, Query, Body, Timestamp, IdempotencyKey)
    ↓
2. Secret Key 로 HMAC 서명 생성
    ↓
3. Signature 를 Authorization 헤더에 포함하여 전송
    ↓
API Gateway (Secret Key 보유)
    ↓
4. 동일한 방식으로 서명 재계산
    ↓
5. 클라이언트 서명과 비교 검증 (상수 시간 비교)
    ↓
6. 일치하면 요청 처리, 불일치하면 거부
```

---

## HMAC 서명이란

### HMAC 알고리즘

HMAC 은 **해시 함수**와 **비밀 키**를 조합하여 메시지 인증 코드를 생성하는 방식입니다.

```
HMAC(Key, Message) = Hash(Key ⊕ opad || Hash(Key ⊕ ipad || Message))
```

**특징**:

- 일방향 함수: 서명에서 Secret Key 를 역산할 수 없음
- 고정 길이 출력: 입력 크기와 무관하게 항상 동일한 길이 (예: SHA256 = 64 hex chars)
- 충돌 저항성: 동일한 서명을 만드는 다른 메시지를 찾기 어려움
- 빠른 계산: 실시간 API 요청 검증에 적합

### 서명 vs 암호화

| 구분     | 서명 (Signature)  | 암호화 (Encryption)         |
|--------|-----------------|-----------------------------|
| 목적     | 무결성 + 인증        | 기밀성                        |
| 키      | Secret Key (대칭) | Public/Private Key (비대칭)   |
| 원본 데이터 | 평문 전송           | 암호문 전송                     |
| 연산 비용  | 낮음              | 높음                         |
| 사용 사례  | API 요청 검증       | 데이터 보호                     |

**본 시스템의 선택**: API 요청은 이미 HTTPS 로 암호화되므로, 서명만으로 무결성과 인증을 보장합니다.

---

## 서명 데이터 구조

### 서명에 포함되는 데이터

서명은 다음 6 개 요소를 줄바꿈 (`\n`) 으로 구분하여 생성합니다:

```
METHOD\n
PATH\n
CANONICAL_QUERY\n
IDEMPOTENCY_KEY\n
BODY_CONTENT\n
TIMESTAMP
```

### 각 요소 설명

#### 1. METHOD

HTTP 메서드 (대문자)

```
예: GET, POST, PUT, PATCH, DELETE
```

#### 2. PATH

URI 경로 (Query String 제외)

```
예: /api/v1/payments
```

#### 3. CANONICAL_QUERY

정규화된 Query String (알파벳 순 정렬)

```
원본: status=active&page=2&size=10
정규화: page=2&size=10&status=active
```

#### 4. IDEMPOTENCY_KEY

멱등성 키 (UUID 형식 권장)

```
예: 550e8400-e29b-41d4-a716-446655440000
```

#### 5. BODY_CONTENT

- **useBodyHash = true**: SHA-256 해시 (64 hex chars)
- **useBodyHash = false**: 정규화된 JSON 문자열

```
useBodyHash = true:
  a1b2c3d4e5f6... (64 자)

useBodyHash = false:
  {"amount":1000,"currency":"KRW"}
```

#### 6. TIMESTAMP

ISO 8601 형식 (타임존 포함)

```
예: 2024-01-15T10:30:00+09:00
```

### 실제 예시

```
POST
/api/v1/payments
orderId=ORD123&userId=USR456
550e8400-e29b-41d4-a716-446655440000
a1b2c3d4e5f6789... (Body SHA-256 Hash)
2024-01-15T10:30:00+09:00
```

이 데이터를 HMAC-SHA256 으로 서명하면:

```
Signature: 9f8e7d6c5b4a3210fedcba9876543210abcdef1234567890abcdef1234567890
```

---

## 서명 생성 프로세스

### 단계별 프로세스

#### Step 1: URI 분리

```java
String path = "/api/v1/payments";
String queryString = "status=active&orderId=123";
```

#### Step 2: Query String 정규화

```java
// CryptoUtil 사용
String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);
// 결과: "orderId=123&status=active" (알파벳 순)
```

#### Step 3: Body Hash 계산 (선택적)

```java
String body = "{\"amount\":1000,\"currency\":\"KRW\"}";
String bodyHash = cryptoUtil.calculateBodyHash(body);
// 결과: "a1b2c3d4..." (64 자 hex)
```

#### Step 4: 서명 생성

```java
// 이미 정규화된 canonicalQuery 사용
String signature = cryptoUtil.generateSignature(
    method,           // POST
    path,             // /api/v1/payments
    canonicalQuery,   // orderId=123&status=active (이미 정규화됨)
    idempotencyKey,   // 550e8400-...
    body,             // 원본 body (내부에서 Hash 처리)
    timestamp,        // 2024-01-15T10:30:00+09:00
    secretKey,
    true,             // useBodyHash
    "HmacSHA256"
);
```

#### Step 5: Authorization 헤더 구성

```
Authorization: algorithm=HmacSHA256, access-key=abc123, signed-date=2024-01-15T10:30:00+09:00, signature=9f8e7d6c..., idempotency-key=550e8400-...
```

### CryptoUtil 사용 예시

```java
@Service
public class ApiClient {
    private final CryptoUtil cryptoUtil;

    public void sendRequest(ApiRequest request) {
        // 1. URI 분리
        String path = request.getPath();
        String queryString = request.getQueryString();

        // 2. Query String 정규화 (1 회만)
        String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);

        // 3. 타임스탬프 생성
        String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // 4. Idempotency Key 생성
        String idempotencyKey = UUID.randomUUID().toString();

        // 5. 서명 생성 (이미 정규화된 canonicalQuery 사용)
        String signature = cryptoUtil.generateSignature(
                request.getMethod(),
                path,
                canonicalQuery,           // 이미 정규화됨
                idempotencyKey,
                request.getBody(),
                timestamp,
                secretKey,
                true,                     // useBodyHash
                "HmacSHA256"
        );

        // 6. 요청 전송
        sendHttpRequest(request, signature, timestamp, idempotencyKey);
    }
}
```

---

## 서명 검증 프로세스

### API Gateway 검증 흐름

```
1. Authorization 헤더 파싱
    ↓
2. Access Key 로 Secret Key 조회 (DB/Cache)
    ↓
3. Timestamp 검증 (허용 범위 내인지)
    ↓
4. Idempotency Key 검증 (중복 요청인지)
    ↓
5. 서명 재계산 (클라이언트와 동일한 방식)
    ↓
6. 서명 비교 (상수 시간 비교)
    ↓
7. 일치하면 통과, 불일치하면 401 Unauthorized
```

### 검증 구현

```java
@Component
public class ApiKeyAuthenticationFilter {
    private final CryptoUtil cryptoUtil;
    private final ApiKeyCacheService apiKeyCacheService;

    private Mono<Boolean> validateSignature(
            ServerWebExchange exchange,
            ApiKeyDto apiKeyDto,
            String signature,
            String idempotencyKey) {

        ServerHttpRequest request = exchange.getRequest();

        // 1. URI 정보 추출
        String path = request.getURI().getPath();
        String queryString = request.getURI().getQuery();

        // 2. Query String 정규화 (1 회만)
        String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);

        // 3. Body 읽기 (RequestBodyCacheFilter 가 캐싱한 값)
        String body = exchange.getAttribute(ExchangeAttributes.REQUEST_BODY);
        final String finalBody = (body != null) ? body : "";

        // 4. 인증 정보 추출
        AuthCredentials credentials = exchange.getAttribute(ExchangeAttributes.AUTH_CREDENTIALS);

        // 5. 알고리즘 결정
        String algorithm = credentials.getAlgorithm();
        if (algorithm == null || algorithm.isEmpty()) {
            algorithm = "HmacSHA256"; // 기본값
        }

        // 6. 서명 검증 (이미 정규화된 canonicalQuery 사용)
        boolean isValid = cryptoUtil.verifyApiRequestSignature(
                request.getMethod().name(),
                path,
                canonicalQuery,           // 이미 정규화됨
                idempotencyKey,
                finalBody,
                credentials.getSignedDate(),
                apiKeyDto.getSecret(),
                signature,
                true,                     // useBodyHash = true
                algorithm
        );

        return Mono.just(isValid);
    }
}
```

### 상수 시간 비교 (Timing Attack 방지)

```java
// CryptoUtil 내부 구현
private boolean constantTimeEquals(String a, String b) {
    if (a == null && b == null) {
        return true;
    }
    if (a == null || b == null) {
        return false;
    }

    if (a.length() != b.length()) {
        return false;
    }

    int result = 0;
    for (int i = 0; i < a.length(); i++) {
        result |= a.charAt(i) ^ b.charAt(i);
    }

    return result == 0;
}
```

**중요**: 일반적인 `equals()` 비교는 첫 불일치 발견 시 즉시 반환하므로, 시간 차이로 비밀 키를 추측할 수 있습니다. 상수 시간 비교는 항상 동일한 시간이 소요되어 이를 방지합니다.

---

## 지원 알고리즘

### HMAC 알고리즘 종류

| 알고리즘           | 해시 함수   | 출력 길이         | 보안 강도 | 권장 사용       |
|----------------|---------|---------------|-------|-------------|
| **HmacSHA256** | SHA-256 | 64 hex chars  | 높음    | ✅ 권장 (기본값)  |
| **HmacSHA384** | SHA-384 | 96 hex chars  | 매우 높음 | 고보안 환경      |
| **HmacSHA512** | SHA-512 | 128 hex chars | 최고    | 금융/결제       |
| HmacSHA1       | SHA-1   | 40 hex chars  | 낮음    | ❌ 비권장 (레거시) |
| HmacMD5        | MD5     | 32 hex chars  | 매우 낮음 | ❌ 사용 금지     |

### 알고리즘 선택 가이드

#### HmacSHA256 (기본값)

```java
String signature = cryptoUtil.generateSignature(
        method, path, canonicalQuery, idempotencyKey,
        body, timestamp, secretKey,
        true, "HmacSHA256"
);
```

- **장점**: 빠른 속도 + 충분한 보안
- **사용 사례**: 일반 API 요청

#### HmacSHA512 (고보안)

```java
String signature = cryptoUtil.generateSignature(
        method, path, canonicalQuery, idempotencyKey,
        body, timestamp, secretKey,
        true, "HmacSHA512"
);
```

- **장점**: 최고 수준 보안
- **단점**: 약간 느린 속도
- **사용 사례**: 금융 거래, 결제 API

### Authorization 헤더 형식

```
Authorization: algorithm={ALGORITHM}, access-key={key}, signed-date={ts}, signature={sig}, idempotency-key={id}
```

예시:

```
Authorization: algorithm=HmacSHA256, access-key=abc123, signed-date=2024-01-15T10:30:00+09:00, signature=9f8e7d6c..., idempotency-key=550e8400-...

Authorization: algorithm=HmacSHA512, access-key=abc123, signed-date=2024-01-15T10:30:00+09:00, signature=1a2b3c4d..., idempotency-key=550e8400-...
```

---

## Query String 정규화

### 정규화의 필요성

Query String 의 순서는 의미가 없지만, 서명 계산에는 순서가 영향을 줍니다:

```
원본 1: ?status=active&page=2
원본 2: ?page=2&status=active

→ 동일한 요청이지만 서명이 달라짐!
```

**해결**: 파라미터를 알파벳 순으로 정렬하여 정규화

### 정규화 알고리즘

```java
// CryptoUtil.java
public String canonicalizeQueryString(String queryString) {
    if (queryString == null || queryString.isEmpty()) {
        return "";
    }

    // TreeMap: 자동으로 키를 알파벳 순 정렬
    Map<String, String> params = new TreeMap<>();

    // 파라미터 파싱
    String[] pairs = queryString.split("&");
    for (String pair : pairs) {
        String[] keyValue = pair.split("=", 2);
        if (keyValue.length == 2) {
            params.put(keyValue[0], keyValue[1]);
        } else if (keyValue.length == 1) {
            params.put(keyValue[0], "");
        }
    }

    // 정렬된 순서로 재구성
    return params.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
}
```

### 정규화 예시

| 원본 Query String                | 정규화 결과                         |
|--------------------------------|--------------------------------|
| `status=active&page=2&size=10` | `page=2&size=10&status=active` |
| `b=2&a=1&c=3`                  | `a=1&b=2&c=3`                  |
| `key=value`                    | `key=value` (단일 파라미터)          |
| `empty=&key=value`             | `empty=&key=value` (빈 값 유지)    |
| `` (빈 문자열)                     | `` (빈 문자열 반환)                  |

### URL 인코딩 처리

Query String 에 특수 문자가 포함된 경우:

```java
// ✅ 올바른 처리
String queryString = "name=" + URLEncoder.encode("홍길동", "UTF-8");
// 결과: "name=%ED%99%8D%EA%B8%B8%EB%8F%99"

// ❌ 잘못된 처리
String queryString = "name=홍길동";  // 인코딩 누락
```

**중요**: URL 인코딩은 서명 생성 **이전**에 수행해야 합니다.

---

## Body Hash 계산

### Body Hash 의 목적

1. **무결성 보장**: Body 내용이 변조되지 않았음을 검증
2. **서명 크기 고정**: 대용량 Body 도 항상 64 자로 고정
3. **로깅 효율**: 서명 데이터가 항상 일정한 크기

### Body Hash 계산 과정

#### Step 1: JSON 정규화

```java
// CryptoUtil.java
private String normalizeJson(String jsonString) {
    if (jsonString == null || jsonString.isEmpty()) {
        return jsonString;
    }

    // JSON 인지 간단히 확인
    String trimmed = jsonString.trim();
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        return jsonString; // JSON 이 아니면 원본 반환
    }

    try {
        // JSON 파싱 후 재직렬화하여 정규화
        Object json = objectMapper.readValue(jsonString, Object.class);
        return objectMapper.writeValueAsString(json);
    } catch (Exception e) {
        // JSON 파싱 실패 시 원본 반환
        log.debug("Failed to normalize JSON, using original: {}", e.getMessage());
        return jsonString;
    }
}
```

#### Step 2: SHA-256 해시

```java
// CryptoUtil.java
public String calculateBodyHash(String body) {
    if (body == null || body.isEmpty()) {
        return "";
    }

    try {
        // JSON 정규화 시도
        String normalizedBody = normalizeJson(body);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(normalizedBody.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    } catch (NoSuchAlgorithmException e) {
        log.error("Failed to calculate SHA-256 hash", e);
        throw new RuntimeException("Failed to calculate body hash", e);
    }
}
```

### Body Hash vs Raw Body

#### useBodyHash = true (권장)

```java
String signature = cryptoUtil.generateSignature(
        ..., body, ..., true, "HmacSHA256"
);
```

- **장점**: 서명 크기 고정 (64 chars), 로깅 효율
- **단점**: SHA-256 연산 오버헤드 (~1ms)
- **사용 사례**: 모든 POST/PUT/PATCH 요청

#### useBodyHash = false (비권장)

```java
String signature = cryptoUtil.generateSignature(
        ..., body, ..., false, "HmacSHA256"
);
```

- **장점**: SHA-256 연산 생략
- **단점**: 대용량 Body 시 서명 크기 증가
- **사용 사례**: GET/DELETE 요청 (Body 없음)

### 성능 비교

| Body 크기 | useBodyHash = true            | useBodyHash = false | 차이           |
|---------|-------------------------------|---------------------|--------------|
| < 1KB   | JSON 정규화 + SHA-256 + HMAC(64) | JSON 정규화 + HMAC(전체) | 거의 동일        |
| 1~10KB  | JSON 정규화 + SHA-256 + HMAC(64) | JSON 정규화 + HMAC(전체) | Hash 가 약간 빠름 |
| > 10KB  | JSON 정규화 + SHA-256 + HMAC(64) | JSON 정규화 + HMAC(전체) | Hash 가 빠름    |

**결론**: 대부분의 경우 `useBodyHash = true` 권장 (보안 + 로깅 이점)

---

## Replay Attack 방지

### Replay Attack 이란

공격자가 정상 요청을 가로채서 나중에 재전송하는 공격:

```
1. 공격자가 네트워크에서 정상 요청 캡처
   POST /api/v1/withdraw
   Authorization: ... Signature=valid_signature ...
   Body: {"amount":1000, "account":"user_account"}

2. 공격자가 동일한 요청을 반복 전송 (100 번)
   → 100 만원 출금 (원래 1 만원만 출금하려 했음)
```

### 방어 메커니즘

#### 1. Timestamp 검증

```java
// TimestampValidator.java
public ValidationResult validate(String timestamp, String accessKey) {
    try {
        ZonedDateTime requestTime = ZonedDateTime.parse(timestamp);
        ZonedDateTime now = ZonedDateTime.now();

        // 시간 차이 계산
        long diffSeconds = Math.abs(Duration.between(now, requestTime).getSeconds());

        // 허용 범위 (기본: 60 초)
        long toleranceSeconds = securityConfig.getAuthentication()
                .getApiKey()
                .getTimestampToleranceSeconds();

        if (diffSeconds > toleranceSeconds) {
            return ValidationResult.invalid(
                    "Request timestamp is too old or future. Diff: " + diffSeconds + "s");
        }

        return ValidationResult.valid();

    } catch (Exception e) {
        log.error("Failed to validate timestamp", e);
        return ValidationResult.invalid("Invalid timestamp format");
    }
}
```

**설정 예시**:

```yaml
security:
  layers:
    authentication:
      api-key:
        timestamp-tolerance-seconds: 60  # Local: 60s, Production: 30s
```

#### 2. Idempotency Key 검증

```java
// IdempotencyService.java
@Service
public class IdempotencyService {
    private final RedisTemplate<String, String> redisTemplate;

    public Mono<CheckResult> checkAndMarkProcessing(String accessKey, String idempotencyKey) {
        String key = "idempotency:" + accessKey + ":" + idempotencyKey;

        // Redis setIfAbsent: 키가 없으면 설정, 있으면 false 반환
        return Mono.fromCallable(() -> {
                    Boolean success = redisTemplate.opsForValue()
                            .setIfAbsent(key, "PROCESSING", 10, TimeUnit.MINUTES);
                    return Boolean.TRUE.equals(success);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(allowed -> new CheckResult(allowed));
    }

    public Mono<Void> markAsCompleted(String accessKey, String idempotencyKey) {
        String key = "idempotency:" + accessKey + ":" + idempotencyKey;

        // TTL 연장 (재사용 방지)
        return Mono.fromRunnable(() -> {
                    redisTemplate.opsForValue()
                            .set(key, "COMPLETED", 24, TimeUnit.HOURS);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
```

**중요**: Idempotency Key 는 **절대 삭제하지 않고** TTL 만 연장합니다.

#### 3. 통합 방어 흐름

```
1. Timestamp 검증 (60 초 이내)
    ↓ PASS
2. Idempotency Key 검증 (Redis setIfAbsent)
    ↓ PASS (처음 사용)
3. 서명 검증
    ↓ PASS
4. 요청 처리
    ↓ 성공/실패
5. Idempotency Key 완료 표시 (24 시간 TTL)
```

**Replay Attack 시나리오**:

```
1. 정상 요청: Timestamp OK, Idempotency Key 첫 사용 → ✅ 처리
2. 1 분 후 재전송: Timestamp OK, Idempotency Key 중복 → ❌ 거부
3. 2 분 후 재전송: Timestamp NG (60 초 초과) → ❌ 거부
```

### Idempotency Key 생성 규칙

#### 클라이언트 측

```java
// ✅ 권장: UUID v4
String idempotencyKey = UUID.randomUUID().toString();
// 결과: "550e8400-e29b-41d4-a716-446655440000"

// ✅ 대안: Timestamp + Random
String idempotencyKey = System.currentTimeMillis() + "-" +
        UUID.randomUUID().toString().substring(0, 8);
// 결과: "1705284600000-a1b2c3d4"

// ❌ 잘못된 예: 순차 번호
String idempotencyKey = String.valueOf(requestCount++);
// 결과: "1", "2", "3" (예측 가능, 보안 취약)
```

#### 재시도 시 주의사항

```java
// ❌ 잘못된 재시도 (새로운 Idempotency Key)
for (int i = 0; i < 3; i++) {
    String newKey = UUID.randomUUID().toString();
    sendRequest(newKey);  // 매번 새 키 → 중복 처리!
}

// ✅ 올바른 재시도 (동일한 Idempotency Key)
String idempotencyKey = UUID.randomUUID().toString();
for (int i = 0; i < 3; i++) {
    try {
        sendRequest(idempotencyKey);  // 같은 키 → 멱등성 보장
        break;
    } catch (Exception e) {
        Thread.sleep(1000);
    }
}
```

---

## 구현 예시

### 클라이언트 구현 (Java)

```java
@Service
public class SecureApiClient {
    private final CryptoUtil cryptoUtil;
    private final RestTemplate restTemplate;
    private final String accessKey;
    private final String secretKey;

    public ApiResponse sendPaymentRequest(PaymentRequest payment) {
        // 1. 기본 정보 준비
        String method = "POST";
        String path = "/api/v1/payments";
        String queryString = "clientId=" + payment.getClientId();
        String body = toJson(payment);

        // 2. Query String 정규화 (1 회만)
        String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);

        // 3. Timestamp 생성 (KST)
        String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // 4. Idempotency Key 생성
        String idempotencyKey = UUID.randomUUID().toString();

        // 5. 서명 생성 (이미 정규화된 canonicalQuery 사용)
        String signature = cryptoUtil.generateSignature(
                method,
                path,
                canonicalQuery,
                idempotencyKey,
                body,
                timestamp,
                secretKey,
                true,                // useBodyHash
                "HmacSHA256"
        );

        // 6. HTTP 요청 구성
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", String.format(
                "algorithm=HmacSHA256, access-key=%s, signed-date=%s, signature=%s, idempotency-key=%s",
                accessKey, timestamp, signature, idempotencyKey
        ));
        headers.setContentType(MediaType.APPLICATION_JSON);

        String fullUrl = "https://api.example.com" + path + "?" + queryString;

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                fullUrl, entity, ApiResponse.class
        );

        return response.getBody();
    }
}
```

### 서버 구현 (API Gateway)

```java
@Component
public class ApiKeyAuthenticationFilter extends AbstractGatewayFilterFactory<Config> {

    private final CryptoUtil cryptoUtil;
    private final ApiKeyCacheService apiKeyCacheService;
    private final IdempotencyService idempotencyService;
    private final TimestampValidator timestampValidator;

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Authorization 헤더 파싱
            AuthCredentials credentials = parseAuthorizationHeader(request);

            // 2. Timestamp 검증
            if (!timestampValidator.validate(credentials.getSignedDate(), credentials.getAccessKey()).isValid()) {
                return Mono.error(new InvalidApiKeyException("Invalid timestamp"));
            }

            // 3. Idempotency Key 검증
            return idempotencyService.checkAndMarkProcessing(
                            credentials.getAccessKey(), credentials.getIdempotencyKey())
                    .flatMap(result -> {
                        if (!result.isAllowed()) {
                            return Mono.error(new DuplicateRequestException("Duplicate request"));
                        }

                        // 4. API Key 조회
                        return apiKeyCacheService.findByAccessKey(credentials.getAccessKey());
                    })
                    .flatMap(apiKey -> {
                        // 5. URI 정보 추출
                        String path = request.getURI().getPath();
                        String queryString = request.getURI().getQuery();

                        // 6. Query String 정규화 (1 회만)
                        String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);

                        // 7. Body 읽기 (RequestBodyCacheFilter 가 캐싱한 값)
                        String body = exchange.getAttribute(ExchangeAttributes.REQUEST_BODY);
                        final String finalBody = (body != null) ? body : "";

                        // 8. 알고리즘 결정
                        String algorithm = credentials.getAlgorithm();
                        if (algorithm == null || algorithm.isEmpty()) {
                            algorithm = "HmacSHA256";
                        }

                        // 9. 서명 검증 (이미 정규화된 canonicalQuery 사용)
                        boolean isValid = cryptoUtil.verifyApiRequestSignature(
                                request.getMethod().name(),
                                path,
                                canonicalQuery,
                                credentials.getIdempotencyKey(),
                                finalBody,
                                credentials.getSignedDate(),
                                apiKey.getSecret(),
                                credentials.getSignature(),
                                true,                // useBodyHash = true
                                algorithm
                        );

                        if (!isValid) {
                            return Mono.error(new InvalidApiKeyException("Invalid signature"));
                        }

                        // 10. 검증 성공 → 요청 처리
                        exchange.getAttributes().put(ExchangeAttributes.CLIENT_ID, apiKey.getClientId());
                        return chain.filter(exchange)
                                .doFinally(signalType -> {
                                    // 11. 완료 표시 (성공/실패 모두)
                                    idempotencyService.markAsCompleted(
                                            credentials.getAccessKey(),
                                            credentials.getIdempotencyKey()
                                    ).subscribe();
                                });
                    });
        };
    }
}
```

### 서명 테스트 API

```java
// SignatureTestService.java (api-key-service)
@Service
public class SignatureTestService {
    private final CryptoUtil cryptoUtil;

    public SignatureTestResponse generateSignature(SignatureTestRequest request) {
        // 1. URI 와 Query String 처리
        String path = request.getUri();
        String queryString = request.getQueryString();

        // 2. Query String 정규화 (1 회만 수행)
        String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);

        // 3. Timestamp 생성
        String timestamp = request.getTimestamp();
        if (timestamp == null || timestamp.isEmpty()) {
            timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        // 4. Idempotency Key 생성
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        // 5. Body 처리
        String bodyString = convertRequestBodyToString(request.getRequestBody());

        // 6. 알고리즘 결정
        String algorithm = request.getAlgorithm();
        if (algorithm == null || algorithm.isEmpty()) {
            algorithm = "HmacSHA256";
        }

        // 7. Body Hash 계산
        String bodyHash = cryptoUtil.calculateBodyHash(bodyString);

        // 8. 서명 생성 (이미 정규화된 canonicalQuery 사용)
        String signature = cryptoUtil.generateSignature(
                request.getMethod().toUpperCase(),
                path,
                canonicalQuery,
                idempotencyKey,
                bodyString,
                timestamp,
                request.getSecretKey(),
                true,  // useBodyHash = true
                algorithm
        );

        // 9. 서명 데이터 재구성 (디버그 정보용)
        String message = request.getMethod().toUpperCase() + "\n" +
                path + "\n" +
                canonicalQuery + "\n" +
                idempotencyKey + "\n" +
                bodyHash + "\n" +
                timestamp;

        // 10. Authorization 헤더 생성
        String authorizationHeader = String.format(
                "algorithm=%s, access-key=%s, signed-date=%s, signature=%s, idempotency-key=%s",
                algorithm, request.getAccessKey(), timestamp, signature, idempotencyKey
        );

        return SignatureTestResponse.builder()
                .signature(signature)
                .authorizationHeader(authorizationHeader)
                .stringToSign(message)
                .timestamp(timestamp)
                .accessKey(request.getAccessKey())
                .build();
    }
}
```

---

## 보안 고려사항

### 1. Secret Key 관리

#### ✅ 안전한 저장

```java
// AES-256 암호화하여 DB 저장
String encryptedSecret = cryptoUtil.encrypt(secretKey, masterKey);
database.save(accessKey, encryptedSecret);

// 사용 시 복호화
String secretKey = cryptoUtil.decrypt(encryptedSecret, masterKey);
```

#### ❌ 위험한 저장

```java
// 평문 저장 (절대 금지!)
database.save(accessKey, secretKey);

// 로그 출력 (절대 금지!)
log.info("Secret Key: {}", secretKey);
```

#### Secret Key 로테이션

```java
// 주기적으로 Secret Key 교체
public void rotateSecretKey(String accessKey) {
    String newSecretKey = generateSecretKey();
    String encrypted = cryptoUtil.encrypt(newSecretKey, masterKey);

    apiKeyRepository.updateSecret(accessKey, encrypted);

    // 클라이언트에게 안전하게 전달 (일회성 URL 등)
    notifyClient(accessKey, newSecretKey);
}
```

### 2. Timing Attack 방어

```java
// ❌ 취약한 비교
public boolean verifySignature(String expected, String provided) {
    return expected.equals(provided);  // 첫 불일치 시 즉시 반환
}

// ✅ 안전한 비교 (CryptoUtil 구현)
private boolean constantTimeEquals(String a, String b) {
    if (a == null && b == null) {
        return true;
    }
    if (a == null || b == null) {
        return false;
    }

    if (a.length() != b.length()) {
        return false;
    }

    int result = 0;
    for (int i = 0; i < a.length(); i++) {
        result |= a.charAt(i) ^ b.charAt(i);
    }

    return result == 0;  // 항상 모든 문자 비교
}
```

### 3. IP Whitelist

```java
// ApiKeyAuthenticationFilter.java
private boolean validateIpWhitelist(ApiKeyDto apiKeyDto, String ipAddress) {
    // SecurityLayerConfig 에서 통합된 설정 확인
    SecurityLayerConfig.Authentication.APIKey apiKeyConfig =
            securityConfig.getAuthentication().getApiKey();

    boolean ipWhitelistEnabled = apiKeyConfig.getSecurity().isIpWhitelistEnabled();

    if (!ipWhitelistEnabled) {
        return true; // 설정 비활성화된 경우 통과
    }

    String allowedIps = apiKeyDto.getAllowedIps();
    if (allowedIps == null || allowedIps.isEmpty()) {
        return true; // 화이트리스트가 설정되지 않은 경우 통과
    }

    // 와일드카드 체크
    if (allowedIps.contains("*")) {
        return true;
    }

    // 허용된 IP 목록과 비교 (정규화된 주소로 비교)
    String[] allowedIpList = allowedIps.split(",");
    for (String allowedIp : allowedIpList) {
        String normalizedAllowedIp = IpAddressUtil.normalizeIpAddress(allowedIp.trim());
        if (ipAddress.equalsIgnoreCase(normalizedAllowedIp)) {
            return true;
        }
    }

    log.warn("IP not in whitelist: {} | AccessKey: {} | Allowed IPs: {}",
            ipAddress, apiKeyDto.getAccessKey(), allowedIps);

    return false;
}
```

### 4. Rate Limiting

```java
// Rate Limiting 은 API Gateway 의 Pre-Validation Layer 에서 처리
// SecurityLayerConfig 를 통한 설정
security:
  layers:
    pre-validation:
      rate-limiting:
        enabled: true
        requests-per-minute: 500
```

### 5. 감사 로깅

```java
// SecurityAuditService.java
@Service
public class SecurityAuditService {
    private final KafkaTemplate<String, SecurityAuditEvent> kafkaTemplate;

    public Mono<Void> publishApiKeyValidationFailure(
            String requestId, String accessKey, String ipAddress, String reason) {

        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType(EventType.API_KEY_VALIDATION_FAILURE)
                .requestId(requestId)
                .accessKey(maskAccessKey(accessKey))
                .clientIp(ipAddress)
                .failureReason(reason)
                .timestamp(Instant.now())
                .build();

        return Mono.fromFuture(
                kafkaTemplate.send(KafkaTopics.SECURITY_AUDIT, event)
        ).then();
    }

    private String maskAccessKey(String accessKey) {
        if (accessKey == null || accessKey.length() <= 8) {
            return "***";
        }
        return accessKey.substring(0, 4) + "***" +
                accessKey.substring(accessKey.length() - 4);
    }
}
```

---

## 성능 최적화

### 1. Query String 정규화 중복 제거

**핵심 원칙**: Query String 정규화는 1 회만 수행하여 중복 연산 제거

#### ❌ 비효율적 (중복 연산)

```java
// 서명 생성 시 내부에서 정규화 수행
String signature = cryptoUtil.generateSignature(..., queryString, ...);

// 디버그 메시지용으로 또 정규화 수행
String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);
```

#### ✅ 효율적 (1 회만 수행)

```java
// Query String 정규화 (1 회만)
String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);

// 서명 생성 (이미 정규화된 값 사용)
String signature = cryptoUtil.generateSignature(..., canonicalQuery, ...);

// 디버그 메시지 (동일한 값 재사용)
String message = method + "\n" + path + "\n" + canonicalQuery + ...;
```

**성능 개선**: 요청당 ~0.5ms 절약

### 2. Secret Key 캐싱

```java
// ApiKeyCacheService.java
@Service
public class ApiKeyCacheService {
    private final ApiKeyRepository apiKeyRepository;
    private final RedisTemplate<String, ApiKeyDto> redisTemplate;

    private static final String API_KEY_CACHE_PREFIX = "api_key:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public Mono<ApiKeyDto> findByAccessKey(String accessKey) {
        String cacheKey = API_KEY_CACHE_PREFIX + accessKey;

        // Redis 캐시 조회
        return Mono.fromCallable(() ->
                        redisTemplate.opsForValue().get(cacheKey))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(cached -> {
                    if (cached != null) {
                        return Mono.just(cached);
                    }

                    // DB 조회
                    return Mono.fromCallable(() ->
                                    apiKeyRepository.findByAccessKey(accessKey)
                                            .orElseThrow(() -> new ApiKeyNotFoundException(accessKey)))
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnNext(apiKey -> {
                                // 캐시 저장
                                redisTemplate.opsForValue().set(cacheKey, apiKey, CACHE_TTL);
                            });
                });
    }
}
```

**성능 개선**: DB 조회 ~10ms → 캐시 조회 ~0.1ms

### 3. Body Hash 선택적 사용

```java
// GET/DELETE: Body 없음 → Body Hash 불필요
if ("GET".equals(method) || "DELETE".equals(method)) {
    useBodyHash = false;
}

// POST/PUT/PATCH: Body Hash 사용
else {
    useBodyHash = true;
}
```

### 4. 알고리즘 선택

| 알고리즘       | 속도        | 보안 | 권장 사용  |
|------------|-----------|----|---------|
| HmacSHA256 | 빠름 (기준)   | 높음 | 일반 API |
| HmacSHA512 | 느림 (1.2x) | 최고 | 금융 API |

**벤치마크** (1000 requests):

```
HmacSHA256: 120ms
HmacSHA512: 145ms
```

---

## 문제 해결

### 문제 1: 서명 불일치 (Signature Mismatch)

#### 증상

```
401 Unauthorized
{"errorMessage": "HMAC signature validation failed"}
```

#### 원인 및 해결

##### 1. Query String 순서 불일치

```java
// ❌ 잘못된 예
String queryString = "status=active&page=2";  // 정규화 안 함

// ✅ 올바른 예
String queryString = "status=active&page=2";
String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);
```

##### 2. Body 정규화 차이

```java
// 클라이언트: 공백 포함
String body = "{\"amount\": 1000}";

// 서버: 공백 제거
String body = "{\"amount\":1000}";

// 해결: CryptoUtil 은 자동으로 JSON 정규화 수행
String bodyHash = cryptoUtil.calculateBodyHash(body);
```

##### 3. Timestamp 형식 불일치

```java
// ❌ 잘못된 형식
String timestamp = "2024-01-15 10:30:00";

// ✅ 올바른 형식 (ISO 8601)
String timestamp = "2024-01-15T10:30:00+09:00";
```

##### 4. Secret Key 불일치

```java
// 디버그: Secret Key 확인 (마스킹하여 로그)
log.debug("Secret Key (masked): {}***{}",
          secretKey.substring(0, 4),
          secretKey.substring(secretKey.length() - 4));
```

### 문제 2: Timestamp 오류

#### 증상

```
401 Unauthorized
{"errorMessage": "Request timestamp is too old"}
```

#### 원인 및 해결

##### 1. 서버 시간 동기화

```bash
# NTP 동기화 확인
ntpdate -q time.google.com

# 시간 동기화
sudo ntpdate -s time.google.com
```

##### 2. 타임존 불일치

```java
// ❌ 잘못된 예 (타임존 없음)
String timestamp = LocalDateTime.now().toString();

// ✅ 올바른 예 (타임존 포함)
String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
```

##### 3. 허용 범위 조정

```yaml
# 로컬 개발 환경
security:
  layers:
    authentication:
      api-key:
        timestamp-tolerance-seconds: 300  # 5 분

# 프로덕션
security:
  layers:
    authentication:
      api-key:
        timestamp-tolerance-seconds: 60   # 1 분
```

### 문제 3: Idempotency Key 중복

#### 증상

```
409 Conflict
{"errorMessage": "Duplicate request detected"}
```

#### 원인 및 해결

##### 1. 재시도 시 동일한 키 사용

```java
// ❌ 잘못된 재시도
for (int i = 0; i < 3; i++) {
    sendRequest(UUID.randomUUID().toString());  // 매번 새 키
}

// ✅ 올바른 재시도
String idempotencyKey = UUID.randomUUID().toString();
for (int i = 0; i < 3; i++) {
    try {
        sendRequest(idempotencyKey);  // 같은 키
        break;
    } catch (Exception e) {
        Thread.sleep(1000);
    }
}
```

##### 2. Redis TTL 확인

```bash
# Redis 에서 키 확인
redis-cli GET "idempotency:{accessKey}:{idempotencyKey}"

# TTL 확인
redis-cli TTL "idempotency:{accessKey}:{idempotencyKey}"
```

### 문제 4: 성능 저하

#### 증상

```
서명 생성/검증이 느림 (> 10ms)
```

#### 원인 및 해결

##### 1. Query String 정규화 중복 제거

```java
// ❌ 매번 정규화
for (int i = 0; i < 1000; i++) {
    String signature = cryptoUtil.generateSignature(..., queryString, ...);
}

// ✅ 1 회만 정규화
String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);
for (int i = 0; i < 1000; i++) {
    String signature = cryptoUtil.generateSignature(..., canonicalQuery, ...);
}
```

##### 2. Secret Key 조회 캐싱

```java
// Redis 캐싱 (ApiKeyCacheService)
// TTL: 5 분
```

##### 3. 알고리즘 변경

```java
// HmacSHA512 → HmacSHA256 (20% 빠름)
String signature = cryptoUtil.generateSignature(
        ..., "HmacSHA256"  // HmacSHA512 에서 변경
);
```

### 디버깅 팁

#### 1. 서명 데이터 로깅

```java
String signatureData = method + "\n" +
        path + "\n" +
        canonicalQuery + "\n" +
        idempotencyKey + "\n" +
        bodyHash + "\n" +
        timestamp;

log.debug("Signature Data:\n{}", signatureData);
log.debug("Secret Key (masked): {}***", secretKey.substring(0, 4));
log.debug("Generated Signature: {}", signature);
```

#### 2. curl 테스트

**Step 1: 서명 생성 API 호출**

```bash
curl -X POST http://localhost:18081/api/v1/test/signature/generate \
  -H "Content-Type: application/json" \
  -d '{
    "accessKey": "your-access-key",
    "secretKey": "your-secret-key",
    "method": "POST",
    "uri": "/api/v1/payments",
    "queryString": "orderId=123",
    "requestBody": {
      "amount": 1000,
      "currency": "KRW"
    }
  }' | jq .
```

**Step 2: 응답에서 받은 Authorization 헤더로 실제 API 호출**

```bash
curl -X POST http://localhost:18080/api/v1/payments?orderId=123 \
  -H "Authorization: algorithm=HmacSHA256, access-key=your-access-key, signed-date=2024-01-15T10:30:00+09:00, signature=9f8e7d6c..., idempotency-key=550e8400-..." \
  -H "Content-Type: application/json" \
  -d '{"amount":1000,"currency":"KRW"}' | jq .
```

**참고**: 응답의 `curlExample` 필드에 완성된 curl 명령어가 포함되어 있어 복사하여 바로 실행할 수 있습니다.

#### 3. SignatureTestService 활용

**API 엔드포인트**: `POST /api/v1/test/signature/generate`

##### 방법 1: Access Key 와 Secret Key 직접 입력

```http
POST http://localhost:18081/api/v1/test/signature/generate
Content-Type: application/json

{
  "accessKey": "your-access-key",
  "secretKey": "your-secret-key",
  "method": "POST",
  "uri": "/api/v1/payments",
  "queryString": "orderId=123",
  "requestBody": {
    "amount": 1000,
    "currency": "KRW"
  },
  "algorithm": "HmacSHA256"
}
```

##### 방법 2: 저장된 API Key 사용

```http
POST http://localhost:18081/api/v1/test/signature/generate/{accessKey}
Content-Type: application/json

{
  "method": "POST",
  "uri": "/api/v1/payments",
  "queryString": "orderId=123",
  "requestBody": {
    "amount": 1000,
    "currency": "KRW"
  }
}
```

##### 응답 예시

```json
{
  "data": {
    "signature": "9f8e7d6c5b4a3210fedcba9876543210abcdef1234567890abcdef1234567890",
    "authorizationHeader": "algorithm=HmacSHA256, access-key=your-access-key, signed-date=2024-01-15T10:30:00+09:00, signature=9f8e7d6c..., idempotency-key=550e8400-e29b-41d4-a716-446655440000",
    "headers": {
      "Authorization": "algorithm=HmacSHA256, access-key=your-access-key, signed-date=2024-01-15T10:30:00+09:00, signature=9f8e7d6c..., idempotency-key=550e8400-...",
      "Content-Type": "application/json",
      "X-Body-Hash": "a1b2c3d4e5f6..."
    },
    "stringToSign": "POST\n/api/v1/payments\norderId=123\n550e8400-e29b-41d4-a716-446655440000\na1b2c3d4e5f6...\n2024-01-15T10:30:00+09:00",
    "timestamp": "2024-01-15T10:30:00+09:00",
    "accessKey": "your-access-key",
    "curlExample": "curl -X POST \\\n  -H 'Authorization: algorithm=HmacSHA256, access-key=your-access-key, signed-date=2024-01-15T10:30:00+09:00, signature=9f8e7d6c..., idempotency-key=550e8400-...' \\\n  -H 'Content-Type: application/json' \\\n  -H 'X-Body-Hash: a1b2c3d4e5f6...' \\\n  -d '{\"amount\":1000,\"currency\":\"KRW\"}' \\\n  'http://localhost:18080/api/v1/payments?orderId=123'",
    "debugInfo": {
      "method": "POST",
      "uri": "/api/v1/payments?orderId=123",
      "contentType": "application/json",
      "bodyHash": "a1b2c3d4e5f6...",
      "canonicalRequest": "POST\n/api/v1/payments\norderId=123\n550e8400-e29b-41d4-a716-446655440000\na1b2c3d4e5f6...\n2024-01-15T10:30:00+09:00",
      "hmacAlgorithm": "HmacSHA256"
    }
  }
}
```

##### curl 테스트 예시

```bash
# 1. 서명 생성 (Test API 호출)
curl -X POST http://localhost:18081/api/v1/test/signature/generate \
  -H "Content-Type: application/json" \
  -d '{
    "accessKey": "your-access-key",
    "secretKey": "your-secret-key",
    "method": "POST",
    "uri": "/api/v1/payments",
    "queryString": "orderId=123",
    "requestBody": {
      "amount": 1000,
      "currency": "KRW"
    }
  }'

# 2. 응답에서 받은 curlExample 복사하여 실행
curl -X POST \
  -H 'Authorization: algorithm=HmacSHA256, access-key=your-access-key, signed-date=2024-01-15T10:30:00+09:00, signature=9f8e7d6c..., idempotency-key=550e8400-...' \
  -H 'Content-Type: application/json' \
  -d '{"amount":1000,"currency":"KRW"}' \
  'http://localhost:18080/api/v1/payments?orderId=123'
```

##### 다양한 시나리오 테스트

**GET 요청 (Query String 포함)**

```json
{
  "accessKey": "your-access-key",
  "secretKey": "your-secret-key",
  "method": "GET",
  "uri": "/api/v1/users",
  "queryString": "page=1&size=10&status=active"
}
```

**POST 요청 (Body 포함)**

```json
{
  "accessKey": "your-access-key",
  "secretKey": "your-secret-key",
  "method": "POST",
  "uri": "/api/v1/payments",
  "requestBody": {
    "amount": 5000,
    "currency": "KRW",
    "orderId": "ORD-12345",
    "customerId": "CUST-67890"
  }
}
```

**PUT 요청 (알고리즘 변경)**

```json
{
  "accessKey": "your-access-key",
  "secretKey": "your-secret-key",
  "method": "PUT",
  "uri": "/api/v1/users/123",
  "requestBody": {
    "name": "홍길동",
    "email": "hong@example.com"
  },
  "algorithm": "HmacSHA512"
}
```

**DELETE 요청 (Body 없음)**

```json
{
  "accessKey": "your-access-key",
  "secretKey": "your-secret-key",
  "method": "DELETE",
  "uri": "/api/v1/users/123"
}
```

##### Request 필드 설명

| 필드           | 필수 여부 | 설명                                           | 기본값             |
|--------------|-------|----------------------------------------------|-----------------|
| accessKey    | 선택    | API Access Key (직접 입력 시)                     | -               |
| secretKey    | 선택    | API Secret Key (직접 입력 시)                     | -               |
| method       | 필수    | HTTP 메서드 (GET, POST, PUT, PATCH, DELETE)      | -               |
| uri          | 필수    | 요청 URI (Query String 제외)                     | -               |
| queryString  | 선택    | Query String (예: page=1&size=10)              | null            |
| requestBody  | 선택    | 요청 Body (JSON 객체 또는 문자열)                     | null            |
| timestamp    | 선택    | 타임스탬프 (ISO 8601 형식)                          | 현재 시간 (KST)    |
| idempotencyKey | 선택  | 멱등성 키                                        | 자동 생성 (UUID)   |
| contentType  | 선택    | Content-Type 헤더                              | application/json |
| algorithm    | 선택    | HMAC 알고리즘 (HmacSHA256, HmacSHA512 등)         | HmacSHA256      |

##### Response 필드 설명

| 필드                  | 설명                                    |
|---------------------|---------------------------------------|
| signature           | 생성된 HMAC 서명 (Hex 인코딩)                |
| authorizationHeader | 완성된 Authorization 헤더 값                |
| headers             | 요청에 필요한 전체 헤더 맵                       |
| stringToSign        | 서명 생성에 사용된 원본 문자열 (디버깅용)             |
| timestamp           | 사용된 타임스탬프 (ISO 8601)                  |
| accessKey           | 사용된 Access Key                        |
| curlExample         | 복사하여 바로 실행 가능한 curl 명령어              |
| debugInfo           | 상세 디버깅 정보 (method, uri, bodyHash 등) |

##### 실전 사용 팁

1. **개발 초기 단계**: `방법 1`을 사용하여 Access Key 와 Secret Key 를 직접 입력하여 테스트
2. **통합 테스트**: `방법 2`를 사용하여 실제 저장된 API Key 로 테스트
3. **디버깅**: 응답의 `debugInfo.canonicalRequest`를 확인하여 서명 데이터 검증
4. **빠른 테스트**: 응답의 `curlExample`을 복사하여 바로 실행

##### 완전한 테스트 플로우 예시

결제 API 를 테스트하는 전체 과정:

```bash
# Step 1: 서명 생성 API 호출
curl -X POST http://localhost:18081/api/v1/test/signature/generate \
  -H "Content-Type: application/json" \
  -d '{
    "accessKey": "test-access-key-12345",
    "secretKey": "test-secret-key-67890",
    "method": "POST",
    "uri": "/api/v1/payments",
    "queryString": "orderId=ORD-2024-001&amount=50000",
    "requestBody": {
      "paymentMethod": "card",
      "cardNumber": "1234-5678-9012-3456",
      "currency": "KRW",
      "customerName": "홍길동"
    },
    "algorithm": "HmacSHA256"
  }'

# Step 2: 응답 예시
{
  "data": {
    "signature": "a1b2c3d4e5f67890abcdef1234567890fedcba0987654321abcdef1234567890",
    "authorizationHeader": "algorithm=HmacSHA256, access-key=test-access-key-12345, signed-date=2024-10-20T15:30:45+09:00, signature=a1b2c3d4e5f67890abcdef1234567890fedcba0987654321abcdef1234567890, idempotency-key=d9f8e7c6-b5a4-3210-fedc-ba9876543210",
    "timestamp": "2024-10-20T15:30:45+09:00",
    "accessKey": "test-access-key-12345",
    "curlExample": "curl -X POST \\\n  -H 'Authorization: algorithm=HmacSHA256, access-key=test-access-key-12345, signed-date=2024-10-20T15:30:45+09:00, signature=a1b2c3d4e5f67890abcdef1234567890fedcba0987654321abcdef1234567890, idempotency-key=d9f8e7c6-b5a4-3210-fedc-ba9876543210' \\\n  -H 'Content-Type: application/json' \\\n  -d '{\"paymentMethod\":\"card\",\"cardNumber\":\"1234-5678-9012-3456\",\"currency\":\"KRW\",\"customerName\":\"홍길동\"}' \\\n  'http://localhost:18080/api/v1/payments?orderId=ORD-2024-001&amount=50000'",
    "debugInfo": {
      "method": "POST",
      "uri": "/api/v1/payments?orderId=ORD-2024-001&amount=50000",
      "contentType": "application/json",
      "bodyHash": "e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5",
      "canonicalRequest": "POST\n/api/v1/payments\namount=50000&orderId=ORD-2024-001\nd9f8e7c6-b5a4-3210-fedc-ba9876543210\ne4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5\n2024-10-20T15:30:45+09:00",
      "hmacAlgorithm": "HmacSHA256"
    }
  }
}

# Step 3: curlExample 복사하여 실제 API 호출
curl -X POST \
  -H 'Authorization: algorithm=HmacSHA256, access-key=test-access-key-12345, signed-date=2024-10-20T15:30:45+09:00, signature=a1b2c3d4e5f67890abcdef1234567890fedcba0987654321abcdef1234567890, idempotency-key=d9f8e7c6-b5a4-3210-fedc-ba9876543210' \
  -H 'Content-Type: application/json' \
  -d '{"paymentMethod":"card","cardNumber":"1234-5678-9012-3456","currency":"KRW","customerName":"홍길동"}' \
  'http://localhost:18080/api/v1/payments?orderId=ORD-2024-001&amount=50000'

# Step 4: 성공 응답
{
  "data": {
    "paymentId": "PAY-2024-10-20-001",
    "status": "SUCCESS",
    "amount": 50000,
    "currency": "KRW",
    "processedAt": "2024-10-20T15:30:46+09:00"
  }
}
```

##### 주요 확인 포인트

1. **Query String 정규화**: 응답의 `canonicalRequest`에서 Query String 이 알파벳 순으로 정렬되었는지 확인 (`amount=50000&orderId=ORD-2024-001`)
2. **Body Hash**: JSON Body 가 정규화된 후 SHA-256 해시로 변환되었는지 확인
3. **Timestamp 형식**: ISO 8601 형식에 타임존 (`+09:00`) 이 포함되었는지 확인
4. **Idempotency Key**: UUID v4 형식으로 자동 생성되었는지 확인
5. **Authorization Header**: 모든 파라미터가 올바른 순서와 포맷으로 포함되었는지 확인

##### 에러 케이스 처리

**1. 필수 필드 누락**

```bash
curl -X POST http://localhost:18081/api/v1/test/signature/generate \
  -H "Content-Type: application/json" \
  -d '{
    "accessKey": "test-key",
    "secretKey": "test-secret"
    // method 필드 누락
  }'

# 응답
{
  "errorMessage": "method: HTTP 메서드는 필수입니다"
}
```

**2. 잘못된 HTTP 메서드**

```bash
curl -X POST http://localhost:18081/api/v1/test/signature/generate \
  -H "Content-Type: application/json" \
  -d '{
    "accessKey": "test-key",
    "secretKey": "test-secret",
    "method": "INVALID",
    "uri": "/api/v1/test"
  }'

# 응답
{
  "errorMessage": "method: HTTP 메서드는 GET, POST, PUT, PATCH, DELETE 중 하나여야 합니다"
}
```

**3. 저장되지 않은 API Key (방법 2 사용 시)**

```bash
curl -X POST http://localhost:18081/api/v1/test/signature/generate/nonexistent-key \
  -H "Content-Type: application/json" \
  -d '{
    "method": "GET",
    "uri": "/api/v1/users"
  }'

# 응답
{
  "errorMessage": "API Key not found: nonexistent-key"
}
```

**4. 타임스탬프 형식 오류**

```bash
curl -X POST http://localhost:18081/api/v1/test/signature/generate \
  -H "Content-Type: application/json" \
  -d '{
    "accessKey": "test-key",
    "secretKey": "test-secret",
    "method": "GET",
    "uri": "/api/v1/test",
    "timestamp": "2024-10-20 15:30:45"  // 잘못된 형식
  }'

# 응답
{
  "errorMessage": "timestamp: ISO 8601 형식이어야 합니다 (예: 2024-01-15T10:30:00+09:00)"
}
```

---

## 참고 자료

### 표준 및 사양

- [RFC 2104: HMAC](https://datatracker.ietf.org/doc/html/rfc2104)
- [RFC 6234: SHA-256/384/512](https://datatracker.ietf.org/doc/html/rfc6234)
- [AWS Signature Version 4](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html)

### 관련 코드

- `module-common/src/main/java/com/exec/common/util/CryptoUtil.java`
- `api-gateway/src/main/java/com/exec/api/gateway/filter/ApiKeyAuthenticationFilter.java`
- `exec-services/api-key-service/src/main/java/com/exec/services/apikey/service/SignatureTestService.java`

### 지침 문서

- `CLAUDE.md` - 전체 백엔드 개발 지침
- `module-common/CLAUDE.md` - CryptoUtil 사용 가이드
- `api-gateway/CLAUDE.md` - API Gateway 구현 가이드
- `exec-services/api-key-service/CLAUDE.md` - API Key Service 가이드

---

## 요약 체크리스트

### 클라이언트 개발자

- [ ] Secret Key 를 안전하게 저장 (환경 변수, Vault 등)
- [ ] Query String 을 정규화 (`canonicalizeQueryString`)
- [ ] Timestamp 를 ISO 8601 형식으로 생성 (타임존 포함)
- [ ] Idempotency Key 를 UUID 로 생성
- [ ] Authorization 헤더 형식 준수
- [ ] 재시도 시 동일한 Idempotency Key 사용

### 서버 개발자

- [ ] Secret Key 를 암호화하여 저장
- [ ] Query String 정규화 1 회만 수행 (중복 제거)
- [ ] Timestamp 허용 범위 설정 (60 초 권장)
- [ ] Idempotency Key 를 Redis 로 관리 (TTL 24 시간)
- [ ] 상수 시간 비교로 Timing Attack 방어
- [ ] 서명 검증 실패 시 상세 로그 기록

### 보안 담당자

- [ ] Secret Key 로테이션 정책 수립
- [ ] IP Whitelist 설정 (필요 시)
- [ ] Rate Limiting 적용
- [ ] 감사 로그 수집 (Kafka)
- [ ] 보안 이벤트 모니터링
- [ ] 정기적인 보안 점검

---

**문서 버전**: 2.0
**최종 업데이트**: 2025-01-15
**시스템 버전**: exec-backend 1.0
