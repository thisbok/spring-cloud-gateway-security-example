-- API Keys 테이블 인덱스 및 제약조건 추가
-- Created: 2024-09-23
-- Description: 성능 최적화를 위한 인덱스 추가 및 데이터 무결성 제약조건 설정

-- =============================================================================
-- 1. 기본 제약조건 추가
-- =============================================================================

-- Access Key 고유 제약조건 (가장 중요한 조회 조건)
ALTER TABLE api_keys
ADD CONSTRAINT uk_api_keys_access_key
UNIQUE (access_key);

-- Rate Limit Tier 검증 제약조건
ALTER TABLE api_keys
ADD CONSTRAINT chk_api_keys_rate_limit_tier
CHECK (rate_limit_tier IN ('BASIC', 'STANDARD', 'PREMIUM', 'ENTERPRISE'));

-- Status 검증 제약조건
ALTER TABLE api_keys
ADD CONSTRAINT chk_api_keys_status
CHECK (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'REVOKED'));

-- 소프트 삭제 제약조건
ALTER TABLE api_keys
ADD CONSTRAINT chk_api_keys_is_deleted
CHECK (is_deleted IN (0, 1));

-- =============================================================================
-- 2. 성능 최적화 인덱스 추가
-- =============================================================================

-- 클라이언트 ID 기반 조회용 인덱스 (API Key 목록 조회)
CREATE INDEX idx_api_keys_client_id
ON api_keys (client_id, is_deleted);

-- 상태별 조회용 복합 인덱스 (관리자 페이지용)
CREATE INDEX idx_api_keys_status_deleted
ON api_keys (status, is_deleted, created_at);

-- 만료 검증용 인덱스 (배치 작업용)
CREATE INDEX idx_api_keys_expired_status
ON api_keys (expired_at, status, is_deleted);

-- Rate Limit Tier 별 조회용 인덱스 (통계용)
CREATE INDEX idx_api_keys_rate_limit_tier
ON api_keys (rate_limit_tier, is_deleted);

-- 생성일 기반 조회용 인덱스 (최근 생성된 키 조회)
CREATE INDEX idx_api_keys_created_at
ON api_keys (created_at DESC, is_deleted);

-- =============================================================================
-- 3. 커버링 인덱스 (조회 성능 극대화)
-- =============================================================================

-- 클라이언트별 API Key 목록 조회용 커버링 인덱스
-- 자주 사용되는 필드들을 포함하여 테이블 접근 최소화
CREATE INDEX idx_api_keys_client_covering
ON api_keys (client_id, is_deleted, status, rate_limit_tier, created_at, expired_at);

-- Access Key 검증용 커버링 인덱스
-- API Gateway 에서 인증 시 필요한 모든 정보 포함
CREATE INDEX idx_api_keys_access_covering
ON api_keys (access_key, is_deleted, status, expired_at, allowed_ips, rate_limit_tier);

-- =============================================================================
-- 4. 파티셔닝 준비 (대용량 데이터 대비)
-- =============================================================================

-- 향후 파티셔닝을 위한 created_at 컬럼 최적화
-- (MySQL 8.0 이상에서 월별 파티셔닝 가능)

-- =============================================================================
-- 5. 통계 정보 수집 최적화
-- =============================================================================

-- 옵티마이저가 올바른 실행계획을 세우도록 통계 정보 갱신
ANALYZE TABLE api_keys;