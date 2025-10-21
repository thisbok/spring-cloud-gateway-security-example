# Backend

exec 시스템의 백엔드 마이크로서비스 집합입니다.

## 📑 목차

- [주요 기능](#-주요-기능)
- [프로젝트 구조](#️-프로젝트-구조)
- [빠른 시작](#-빠른-시작)
- [모듈 구성](#-모듈-구성)
- [HMAC 서명 시스템](#-hmac-서명-시스템)
- [보안 아키텍처](#-보안-아키텍처)
- [개발 가이드](#️-개발-가이드)
- [인프라 아키텍처](#️-인프라-아키텍처)
- [데이터베이스](#️-데이터베이스)
- [성능 최적화](#-성능)
- [문제 해결](#-문제-해결)
- [참고 문서](#-참고-문서)

## ✨ 주요 기능

- **🔐 HMAC 서명 인증**: API 요청의 무결성과 인증 보장 (HMAC-SHA256/384/512)
- **🛡️ 4 계층 보안 시스템**: Pre-validation, Authentication, Authorization, Monitoring
- **⚡ 고성능 캐싱**: Redis 기반 API Key 캐싱 (5 분 TTL)
- **🔄 Replay Attack 방지**: Timestamp + Idempotency Key 검증
- **📊 실시간 모니터링**: Prometheus 메트릭, ELK 로깅, Kafka 이벤트
- **🧪 서명 테스트 API**: 개발자 친화적인 HMAC 서명 생성/검증 도구

## 🏗️ 프로젝트 구조

```
backend/
├── module-common/           # 공통 모듈 (API Response, Exception, CryptoUtil)
├── module-core/             # 코어 모듈 (Domain Entity, Repository, Database)
├── api-gateway/             # API Gateway (18080)
│   └── src/main/java/com/exec/api/gateway/
│       ├── filter/          # HMAC 인증, Rate Limiting, 로깅
│       ├── config/          # 보안 계층 설정
│       └── service/         # API Key 캐싱, Idempotency 관리
└── exec-services/           # 마이크로서비스 컨테이너
    ├── api-key-service/     # API Key 관리 서비스 (18081)
    │   └── src/main/java/com/exec/services/apikey/
    │       ├── controller/  # API Key CRUD, 서명 테스트 API
    │       ├── service/     # API Key 관리, HMAC 서명 생성
    │       └── dto/         # 요청/응답 DTO
    └── analytics-service/   # 분석 서비스 (18082)
        └── src/main/java/com/exec/services/analytics/
            ├── consumer/    # Kafka 로그 수신
            ├── service/     # 로그 분석 및 저장
            └── repository/  # Elasticsearch 연동

주요 파일:
- CLAUDE.md                       # 전체 개발 지침
- HMAC_SIGNATURE_GUIDE.md         # HMAC 서명 가이드
- module-common/src/.../CryptoUtil.java    # HMAC 생성/검증
- api-gateway/src/.../ApiKeyAuthenticationFilter.java  # HMAC 검증
- exec-services/api-key-service/src/.../SignatureTestService.java  # 서명 테스트
```

## 🚀 빠른 시작

### 필수 요구사항

- **Java**: 17+
- **Gradle**: 8.11.1+
- **Docker**: 18.06.0+
- **Docker Compose**: 1.28.0+

### 로컬 개발 환경 설정

#### 1. 인프라 시작 (Docker)

백엔드 서비스 실행 전에 필요한 인프라를 먼저 시작해야 합니다.

```bash
# 프로젝트 루트에서 infra 디렉토리로 이동
cd ../infra

# Elasticsearch 사용자 초기화
docker-compose up setup

# 전체 인프라 시작 (백그라운드)
docker-compose up -d

# 인프라 상태 확인
docker-compose ps
```

인프라 서비스가 모두 실행되면 다음 URL 에서 접근 가능합니다:

| 서비스        | URL                    | 자격 증명                  |
| ------------- | ---------------------- | -------------------------- |
| Kibana        | http://localhost:5601  | elastic / changeme         |
| Elasticsearch | http://localhost:9200  | elastic / changeme         |
| Kafka UI      | http://localhost:8080  | -                          |
| Jaeger UI     | http://localhost:16686 | -                          |
| MySQL         | localhost:3306         | elk_user / mysql_user123!@ |
| Redis         | localhost:6379         | -                          |

> **참고**: 인프라 상세 가이드는 [`../infra/README-ko.md`](../infra/README-ko.md) 참조

#### 2. 백엔드 서비스 빌드 및 실행

인프라가 준비되면 백엔드 서비스를 시작합니다.

1. **전체 프로젝트 빌드**

   ```bash
   # backend 디렉토리로 돌아옴
   cd ../backend

   ./gradlew build
   ```

2. **서비스 실행**

   ```bash
   # API Gateway 실행
   cd api-gateway && ./gradlew bootRun

   # API Key Service 실행 (새 터미널)
   cd exec-services/api-key-service && ./gradlew bootRun

   # Analytics Service 실행 (새 터미널, 선택사항)
   cd exec-services/analytics-service && ./gradlew bootRun
   ```

3. **서비스 확인**
   - API Gateway: http://localhost:18080/actuator/health
   - API Key Service: http://localhost:18081/actuator/health
   - Analytics Service: http://localhost:18082/actuator/health

### HMAC 서명 테스트

API 인증 시스템을 테스트하려면:

```bash
# 1. 서명 생성 API 호출
curl -X POST http://localhost:18081/api/v1/test/signature/generate \
  -H "Content-Type: application/json" \
  -d '{
    "accessKey": "test-key",
    "secretKey": "test-secret",
    "method": "GET",
    "uri": "/api/v1/test"
  }'

# 2. 응답의 curlExample 필드를 복사하여 실행
# (응답에 포함된 완성된 curl 명령어를 그대로 사용)
```

상세한 HMAC 서명 가이드는 [HMAC_SIGNATURE_GUIDE.md](./HMAC_SIGNATURE_GUIDE.md) 참조

## 📦 모듈 구성

### module-common

**공통 기능 모듈**

- API Response 표준화 (`ApiResponse<T>`)
- 예외 처리 체계 (`BusinessException`, `ErrorCode`)
- 암호화 유틸리티 (`CryptoUtil` - HMAC 서명 생성/검증)
- 공통 DTO (`ApiCallLogDto`, `ErrorResponse` 등)

### module-core

**데이터 접근 계층 모듈**

- JPA Entity 정의 (`ApiKey`, `BaseEntity`, `SoftDeleteEntity`)
- Repository 인터페이스 (`ApiKeyRepository`)
- 데이터베이스 설정 (Master-Slave 구성, Replication Routing)
- 공통 Enum (`ApiKeyStatus`, `RateLimitTier`, `UserRole`)
- Flyway 마이그레이션 스크립트

### api-gateway

**API Gateway 서비스 (포트: 18080)**

- **인증/인가**: API Key 기반 HMAC 서명 검증
- **보안 필터 체인**:
  - `RequestBodyCacheFilter`: Body 캐싱 (한 번만 읽기)
  - `IntegratedLoggingFilter`: Request ID 생성, 통합 로깅
  - `UnifiedErrorHandlingFilter`: 에러 핸들링
  - `ApiKeyAuthenticationFilter`: HMAC 서명 검증
  - `SecurityAttackDetectionFilter`: SQL Injection, XSS 차단
- **기능**:
  - Rate Limiting (IP/사용자별)
  - Circuit Breaker (장애 격리)
  - API 호출 로깅 (Kafka → Elasticsearch)
  - Idempotency 관리 (Redis)

### api-key-service

**API Key 관리 서비스 (포트: 18081)**

- API Key 생성/조회/수정/삭제 (CRUD)
- Secret Key AES-256 암호화 저장
- Redis 캐싱 (5 분 TTL)
- **서명 테스트 API**: HMAC 서명 생성 및 디버깅 도구
  - `POST /api/v1/test/signature/generate` - 직접 키 입력
  - `POST /api/v1/test/signature/generate/{accessKey}` - 저장된 키 사용
- Rate Limit 설정 (분/시간/일별)
- 사용량 추적 (최근 사용 시각)

### analytics-service

**분석 서비스 (포트: 18082)**

- Kafka 로그 수신 (API 호출 로그, 보안 감사 로그)
- Elasticsearch 저장 및 인덱싱
- 실시간 메트릭 수집
- 알림 서비스 (이상 징후 탐지)

## 🔐 HMAC 서명 시스템

### 개요

API 요청의 무결성과 인증을 보장하기 위해 HMAC (Hash-based Message Authentication Code) 서명 시스템을 사용합니다.

### 주요 기능

- **HMAC-SHA256/384/512**: 다양한 해시 알고리즘 지원
- **Replay Attack 방지**: Timestamp + Idempotency Key
- **Query String 정규화**: 파라미터 순서 무관 검증
- **Body Hash**: JSON 정규화 후 SHA-256 해시

### 서명 테스트 API

API Key Service 에서 서명 생성 테스트 API 제공:

```bash
# 서명 생성 API 호출
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

# 응답에서 받은 curlExample 을 복사하여 실제 API 호출
curl -X POST \
  -H 'Authorization: algorithm=HmacSHA256, access-key=..., signed-date=..., signature=..., idempotency-key=...' \
  -H 'Content-Type: application/json' \
  -d '{"amount":1000,"currency":"KRW"}' \
  'http://localhost:18080/api/v1/payments?orderId=123'
```

### Authorization 헤더 형식

```
Authorization: algorithm=HmacSHA256, access-key={key}, signed-date={ts}, signature={sig}, idempotency-key={id}
```

### 상세 가이드

HMAC 서명 생성/검증에 대한 상세한 가이드는 [HMAC_SIGNATURE_GUIDE.md](./HMAC_SIGNATURE_GUIDE.md) 참조

## 🔒 보안 아키텍처

### 🎯 4 계층 보안 시스템

#### 1. 사전 검증 계층

- **Rate Limiting**: IP/사용자별 요청 제한 (분/시/일)
- **DDoS 방어**: 분산 공격 탐지 및 자동 차단
- **Input Validation**: SQL Injection, XSS, Path Traversal 차단
- **지리적 차단**: 특정 국가 IP 차단

#### 2. 인증 계층

- **API Key 인증**: HMAC 서명 검증, Timestamp 검증
- **Idempotency Key**: 중복 요청 방지 (Redis)
- **IP Whitelist**: 허용된 IP 에서만 접근

#### 3. 인가 계층

- **RBAC**: 역할 기반 접근 제어 (USER, ADMIN, SUPER_ADMIN)
- **Scope 기반 권한**: API Key 별 허용 범위 설정

#### 4. 모니터링 계층

- **Audit Logging**: 모든 보안 이벤트 Kafka 전송
- **실시간 분석**: 이상 징후 탐지 및 위험도 평가
- **자동 알림**: Slack, Email 다채널 알림

### 보안 필터 체인

```
요청 → RequestBodyCacheFilter → IntegratedLoggingFilter → UnifiedErrorHandlingFilter
    → SecurityAttackDetectionFilter → ApiKeyAuthenticationFilter → 대상 서비스
```

### 환경별 보안 레벨

- **Local**: 개발 편의성 우선, HMAC 서명 선택적
- **Development**: 보안 테스트를 위한 중간 레벨
- **Production**: 최고 보안 레벨, 모든 보안 기능 활성화

## 🛠️ 개발 가이드

### 새로운 서비스 추가

1. **서비스 디렉토리 생성**

   ```bash
   mkdir exec-services/new-service
   ```

2. **build.gradle 생성**

   ```gradle
   dependencies {
       implementation project(':module-core')  // transitive 로 module-common 포함
   }
   ```

3. **settings.gradle 에 추가**
   ```gradle
   include 'exec-services:new-service'
   ```

### 빌드 명령어

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :module-core:build
./gradlew :api-gateway:build
./gradlew :exec-services:api-key-service:build

# 테스트 실행
./gradlew test

# 특정 서비스만 실행
./gradlew :api-gateway:bootRun
```

### 개발 규칙

#### 패키지 구조

- **Gateway**: `com.exec.api.gateway`
- **Service**: `com.exec.services.{service-name}`
- **Common**: `com.exec.common`
- **Core**: `com.exec.core`

#### API 응답 표준

```java
// 성공 응답 (데이터 포함)
return ApiResponse.success(data);

// 성공 응답 (데이터 없음)
return ApiResponse.success();

// 에러 응답
return ApiResponse.error("Error message");
```

**응답 구조**:

```json
// 성공
{
  "data": { ... }
}

// 에러
{
  "errorMessage": "Error message"
}
```

#### 예외 처리

```java
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
```

#### 로깅 규칙

```java
// Request ID 기반 로깅
String requestId = exchange.getAttribute(ExchangeAttributes.REQUEST_ID);
log.info("[{}] Processing request", requestId);
```

## 🏗️ 인프라 아키텍처

### 전체 시스템 구성도

```
┌─────────────────────────────────────────────────────────────────┐
│                     Application Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐     │
│  │ API Gateway  │  │ API Key Svc  │  │ Analytics Service │     │
│  │  (18080)     │  │   (18081)    │  │     (18082)       │     │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────────────┘     │
└─────────┼──────────────────┼────────────────┼───────────────────┘
          │                  │                │
          ▼                  ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                          │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Data Storage                                            │   │
│  │  - MySQL 8.0 (3306): 애플리케이션 데이터                │   │
│  │  - Redis (6379): 캐싱 및 Idempotency                     │   │
│  │  - Elasticsearch 8.11.3 (9200): 로그/메트릭 검색        │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Event Streaming                                         │   │
│  │  - Kafka 7.4.0 (9092): 이벤트 스트리밍                  │   │
│  │  - Zookeeper (2181): Kafka 코디네이션                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Processing & Analytics                                  │   │
│  │  - Logstash 8.11.3: 로그 처리 및 인덱싱                 │   │
│  │  - Kibana 8.11.3 (5601): 시각화 및 대시보드             │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Monitoring                                              │   │
│  │  - Jaeger 1.52.0 (16686): 분산 추적                     │   │
│  │  - Kafka UI (8080): Kafka 모니터링                      │   │
│  │  - Prometheus: 메트릭 수집 (Actuator 통합)              │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 데이터 흐름

#### 1. API 요청 처리

```
클라이언트 → API Gateway → 서비스
                ↓
            (로깅)
                ↓
             Kafka → Logstash → Elasticsearch → Kibana
```

#### 2. 캐싱 전략

```
API Gateway / 서비스 → Redis (L1 Cache)
                      ↓ (Cache Miss)
                    MySQL (DB)
```

#### 3. 분산 추적

```
API Gateway / 서비스 → Jaeger Collector → Elasticsearch → Jaeger UI
```

### 인프라 구성 요소

#### MySQL 8.0

- **역할**: 애플리케이션 데이터 영구 저장
- **데이터베이스**: `pg_sandbox` (로컬), `elk_db` (인프라 전용)
- **주요 테이블**: `api_keys`
- **연결 정보**:
  - Write Master: `localhost:3306`
  - Read Replica: `localhost:3306` (로컬 환경에서는 동일)
  - User: `root` / `elk_user`
  - Connection Pool: HikariCP (Max 20, Min 5)

#### Redis

- **역할**: 캐싱 및 Idempotency 관리
- **포트**: 6379
- **주요 용도**:
  - API Key 캐싱 (TTL: 5 분)
  - Idempotency Key 저장 (TTL: 24 시간)
  - Rate Limiting 카운터
- **데이터베이스**: 분리 사용 (Gateway: 0, Analytics: 2)

#### Kafka 7.4.0

- **역할**: 이벤트 스트리밍 플랫폼
- **포트**: 9092 (외부), 29092 (내부)
- **주요 Topic**:
  - `api-gateway-request-logs`: API 요청/응답 로그
  - `api-gateway-request-logs-dlt`: Dead Letter Topic
  - `logs`, `events`, `metrics`: 일반 로그/이벤트
  - `security-audit-logs`: 보안 감사 로그
- **UI**: http://localhost:8080

#### Elasticsearch 8.11.3

- **역할**: 로그 검색 및 분석
- **포트**: 9200 (HTTP), 9300 (TCP)
- **주요 인덱스**:
  - `api-gateway-request-log-*`: API 요청 로그
  - `kafka-logs-*`: Kafka 로그
  - `jaeger-*`: Jaeger 추적 데이터
- **인증**: elastic / changeme (기본값)

#### Logstash 8.11.3

- **역할**: 로그 수집, 변환, 라우팅
- **입력 소스**:
  - Redis `transactions` 리스트
  - Kafka topics (logs, events, metrics)
- **출력**: Elasticsearch
- **포트**: 5044 (Beats), 50000 (TCP), 9600 (Monitoring)

#### Kibana 8.11.3

- **역할**: 데이터 시각화 및 대시보드
- **포트**: 5601
- **접근**: http://localhost:5601
- **주요 기능**:
  - API 요청 로그 분석
  - 실시간 메트릭 모니터링
  - 보안 이벤트 추적

#### Jaeger 1.52.0

- **역할**: 분산 추적 (Distributed Tracing)
- **포트**: 16686 (UI), 14268 (HTTP), 14250 (gRPC)
- **백엔드 저장소**: Elasticsearch
- **추적 전파**: B3 및 W3C 형식 지원

### 환경 설정

인프라 구성은 `infra/.env` 파일에서 관리:

```env
# 버전
ELASTIC_VERSION=8.11.3
JAEGER_VERSION=1.52.0
KAFKA_VERSION=7.4.0

# Elasticsearch 자격 증명
ELASTIC_PASSWORD=changeme
LOGSTASH_INTERNAL_PASSWORD=changeme
KIBANA_SYSTEM_PASSWORD=changeme

# MySQL 자격 증명
MYSQL_ROOT_PASSWORD=mysql_root123!@
MYSQL_DATABASE=elk_db
MYSQL_USER=elk_user
MYSQL_PASSWORD=mysql_user123!@
```

### 인프라 관리 명령어

```bash
# 전체 인프라 시작
docker-compose up -d

# 특정 서비스만 재시작
docker-compose restart kafka elasticsearch

# 로그 확인
docker-compose logs -f [서비스명]

# 인프라 중지 (데이터 유지)
docker-compose stop

# 인프라 완전 삭제 (데이터 포함)
docker-compose down -v

# Kafka Topic 생성
docker-compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic new-topic \
  --partitions 3 \
  --replication-factor 1

# Redis 데이터 확인
docker-compose exec redis redis-cli

# MySQL 접속
docker-compose exec mysql mysql -u elk_user -p elk_db

# Elasticsearch 상태 확인
curl http://localhost:9200/_cluster/health?pretty
```

## 🗄️ 데이터베이스

### 주요 테이블

- `api_keys`: API Key 정보 및 Secret Key (암호화)
- 기타 도메인별 테이블

### 네이밍 규칙

- 테이블명: snake_case (`api_keys`)
- 컬럼명: snake_case (`created_at`)
- 인덱스명: `idx_테이블명_컬럼명` (`idx_api_keys_access_key`)
- 외래키명: `fk_테이블명_참조테이블명`

### 공통 컬럼

모든 테이블은 다음 컬럼 포함:

- `id`: BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT
- `created_at`: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
- `updated_at`: TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP

소프트 삭제 테이블 추가:

- `is_deleted`: TINYINT(1) DEFAULT 0
- `deleted_at`: TIMESTAMP NULL

### 데이터베이스 연결 설정

#### 애플리케이션 데이터베이스 (pg_sandbox)

```yaml
# application-local.yml
spring:
  datasource:
    write:
      url: jdbc:mysql://localhost:3306/pg_sandbox
      username: root
      password: mysql_root123!@
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
    read:
      url: jdbc:mysql://localhost:3306/pg_sandbox
      username: root
      password: mysql_root123!@
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
```

#### Flyway 마이그레이션

데이터베이스 스키마는 Flyway 를 통해 버전 관리:

```bash
# 마이그레이션 파일 위치
module-core/src/main/resources/db/migration/

V001__create_api_keys_table.sql         # api_keys 테이블 생성
V002__add_indexes_and_constraints.sql   # 인덱스 및 제약조건
V003__insert_sample_api_keys.sql        # 샘플 데이터
```

## 📈 성능

### JVM 설정

```bash
-Xms512m -Xmx1g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
```

### HMAC 서명 최적화

- **Query String 정규화**: 1 회만 수행하여 중복 연산 제거 (~0.5ms 절약)
- **Secret Key 캐싱**: Redis 캐싱으로 DB 조회 최소화 (10ms → 0.1ms)
- **알고리즘 선택**: HmacSHA256 (일반 API), HmacSHA512 (금융 API)
- **Body Hash 선택적 사용**: GET/DELETE 요청은 Body Hash 생략

### 데이터베이스 최적화

- 커넥션 풀: HikariCP (최대 20, 최소 5)
- 읽기 전용 복제본 활용 (@ReadOnlyService 어노테이션)
- 적절한 인덱스 구성 (access_key, user_id, status)
- API Key 조회 캐싱 (Redis 5 분 TTL)

### 캐싱 전략

- **L1 Cache**: Redis (5 분 TTL)
- **무효화**: API Key 수정/삭제 시 즉시 캐시 삭제
- **워밍**: 자주 사용되는 키 사전 로드 (선택사항)

## 🔍 문제 해결

### 인프라 관련 문제

1. **인프라 서비스가 시작되지 않음**

   ```bash
   # 인프라 상태 확인
   cd ../infra
   docker-compose ps

   # 모든 서비스 로그 확인
   docker-compose logs

   # 특정 서비스 로그 확인
   docker-compose logs elasticsearch
   docker-compose logs kafka

   # 서비스 재시작
   docker-compose restart [서비스명]

   # 완전히 재시작 (데이터 유지)
   docker-compose down
   docker-compose up setup
   docker-compose up -d
   ```

2. **Elasticsearch 연결 실패**

   ```bash
   # Elasticsearch 상태 확인
   curl http://localhost:9200/_cluster/health?pretty

   # 인증 확인
   curl -u elastic:changeme http://localhost:9200

   # 비밀번호 재설정
   docker-compose exec elasticsearch bin/elasticsearch-reset-password --batch --user elastic
   ```

3. **Kafka 연결 실패**

   ```bash
   # Kafka 상태 확인
   docker-compose logs kafka

   # Topic 목록 확인
   docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

   # Consumer Group 확인
   docker-compose exec kafka kafka-consumer-groups --list --bootstrap-server localhost:9092
   ```

4. **Redis 연결 실패**

   ```bash
   # Redis 연결 테스트
   docker-compose exec redis redis-cli ping

   # Redis 메모리 사용량 확인
   docker-compose exec redis redis-cli INFO memory

   # 모든 키 확인
   docker-compose exec redis redis-cli KEYS "*"
   ```

5. **MySQL 연결 실패**

   ```bash
   # MySQL 연결 테스트
   docker-compose exec mysql mysql -u root -p -e "SELECT 1"

   # 데이터베이스 목록 확인
   docker-compose exec mysql mysql -u root -p -e "SHOW DATABASES"

   # 사용자 권한 확인
   docker-compose exec mysql mysql -u root -p -e "SHOW GRANTS FOR 'elk_user'@'%'"
   ```

6. **디스크 공간 부족**

   ```bash
   # Docker 볼륨 확인
   docker volume ls

   # 사용하지 않는 리소스 정리
   docker system prune -a --volumes

   # Elasticsearch 인덱스 삭제 (오래된 로그)
   curl -X DELETE "http://localhost:9200/api-gateway-request-log-2024.01.*"
   ```

### 애플리케이션 문제

1. **HMAC 서명 불일치 (401 Unauthorized)**

   ```bash
   # 서명 테스트 API 로 디버깅
   curl -X POST http://localhost:18081/api/v1/test/signature/generate \
     -H "Content-Type: application/json" \
     -d '{"accessKey":"your-key","secretKey":"your-secret","method":"GET","uri":"/api/v1/test"}'

   # debugInfo.canonicalRequest 확인:
   # - Query String 이 알파벳 순으로 정렬되었는지
   # - Timestamp 가 ISO 8601 형식인지
   # - Body Hash 가 올바르게 계산되었는지
   ```

   상세 가이드: [HMAC_SIGNATURE_GUIDE.md - 문제 해결](./HMAC_SIGNATURE_GUIDE.md#문제-해결)

2. **포트 충돌**

   ```bash
   # 사용 중인 포트 확인
   lsof -i :18080
   lsof -i :18081
   lsof -i :18082

   # 인프라 포트 확인
   lsof -i :3306   # MySQL
   lsof -i :6379   # Redis
   lsof -i :9092   # Kafka
   lsof -i :9200   # Elasticsearch
   lsof -i :5601   # Kibana
   ```

3. **메모리 부족**

   ```bash
   # JVM 힙 크기 조정
   export JAVA_OPTS="-Xms1g -Xmx2g"
   ./gradlew bootRun

   # Docker 메모리 할당 확인 (Docker Desktop)
   # Preferences > Resources > Memory
   # 최소 4GB 권장, 8GB 이상 이상적
   ```

4. **Timestamp 오류 (Request timestamp is too old)**

   ```bash
   # 서버 시간 동기화 확인
   ntpdate -q time.google.com

   # 타임존 확인 (KST: +09:00)
   date +%z

   # Timestamp tolerance 설정 확인
   # application.yml:
   # security.layers.authentication.api-key.timestamp-tolerance-seconds: 60
   ```

5. **Idempotency Key 중복 (409 Conflict)**

   ```bash
   # Redis 에서 키 확인
   docker-compose exec redis redis-cli GET "idempotency:{accessKey}:{idempotencyKey}"

   # TTL 확인 (24 시간)
   docker-compose exec redis redis-cli TTL "idempotency:{accessKey}:{idempotencyKey}"

   # 특정 키 삭제 (개발 환경에서만)
   docker-compose exec redis redis-cli DEL "idempotency:{accessKey}:{idempotencyKey}"
   ```

6. **Kafka 메시지 전송 실패**

   ```bash
   # Kafka 로그 확인
   docker-compose logs -f kafka

   # Topic 의 메시지 확인
   docker-compose exec kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic api-gateway-request-logs \
     --from-beginning \
     --max-messages 10

   # Producer 설정 확인 (application.yml)
   # spring.kafka.bootstrap-servers: localhost:9092
   ```

7. **Elasticsearch 인덱싱 실패**

   ```bash
   # Analytics Service 로그 확인
   # Elasticsearch 연결 오류 메시지 확인

   # 인덱스 존재 확인
   curl http://localhost:9200/_cat/indices?v

   # 인덱스 매핑 확인
   curl http://localhost:9200/api-gateway-request-log-*/_mapping?pretty
   ```

### 로그 확인

```bash
# 실행 중인 서비스 로그 확인 (각 터미널에서)
# API Gateway 로그는 실행 터미널에서 직접 확인
# 별도 로그 파일이 필요한 경우 application.yml 설정 추가
```

### 디버깅 도구

- **Actuator**: `/actuator/health`, `/actuator/metrics`
- **서명 테스트 API**: `POST /api/v1/test/signature/generate`
- **로그 레벨 조정**: `application-local.yml`에서 `logging.level` 설정

## 🧪 테스트

### 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :module-core:test
./gradlew :api-gateway:test

# 테스트 커버리지
./gradlew jacocoTestReport
```

### 테스트 전략

- 테스트 코드는 AI 기반 도구로 자동 생성
- 테스트 커버리지 목표: 80% 이상
- CI/CD 파이프라인에서 자동 테스트 실행

## 📚 참고 문서

### 개발 가이드

- **[CLAUDE.md](./CLAUDE.md)** - 전체 백엔드 개발 지침 및 아키텍처
- **[HMAC_SIGNATURE_GUIDE.md](./HMAC_SIGNATURE_GUIDE.md)** - HMAC 서명 생성/검증 가이드
- **[module-common/CLAUDE.md](./module-common/CLAUDE.md)** - 공통 모듈 사용 가이드 (CryptoUtil 등)
- **[api-gateway/CLAUDE.md](./api-gateway/CLAUDE.md)** - API Gateway 구현 가이드
- **[exec-services/api-key-service/CLAUDE.md](./exec-services/api-key-service/CLAUDE.md)** - API Key Service 가이드

### 인프라 가이드

- **[../infra/README-ko.md](../infra/README-ko.md)** - Docker 기반 ELK Stack + Redis + MySQL 설정 가이드
- **[../infra/CLAUDE.md](../infra/CLAUDE.md)** - 인프라 관리 명령어 및 개발 노트
- **[../infra/extensions/README.md](../infra/extensions/README.md)** - 선택적 확장 기능 (Filebeat, Metricbeat 등)

### 모듈별 문서

각 모듈 디렉토리의 `CLAUDE.md` 파일에서 해당 모듈의 상세 개발 지침 확인

### 외부 참고 자료

- **Spring Boot**: https://spring.io/projects/spring-boot
- **Spring Cloud Gateway**: https://spring.io/projects/spring-cloud-gateway
- **Elasticsearch**: https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html
- **Apache Kafka**: https://kafka.apache.org/documentation/
- **Jaeger**: https://www.jaegertracing.io/docs/

## 📄 라이선스

Copyright (c) 2025 thisbok. All rights reserved.
