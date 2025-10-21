-- 테스트 및 개발용 샘플 API Key 데이터 삽입
-- Created: 2024-09-23
-- Description: 개발 및 테스트 환경에서 사용할 샘플 API Key 데이터 제공

-- =============================================================================
-- 1. 기본 테스트 API Key 데이터
-- =============================================================================

-- 테스트용 API Key 1 (BASIC 티어)
INSERT INTO api_keys (
    access_key,
    secret,
    client_id,
    rate_limit_tier,
    allowed_ips,
    expired_at,
    description,
    status,
    is_deleted,
    created_at,
    updated_at
) VALUES (
    'test-access-key-001',
    'AES_ENCRYPTED_SECRET_KEY_001', -- 실제 환경에서는 암호화된 값으로 대체
    'client-001',
    'BASIC',
    '127.0.0.1,192.168.1.0/24',
    DATE_ADD(NOW(), INTERVAL 1 YEAR),
    '개발 테스트용 기본 API Key',
    'ACTIVE',
    0,
    NOW(),
    NOW()
);

-- 테스트용 API Key 2 (PREMIUM 티어)
INSERT INTO api_keys (
    access_key,
    secret,
    client_id,
    rate_limit_tier,
    allowed_ips,
    expired_at,
    description,
    status,
    is_deleted,
    created_at,
    updated_at
) VALUES (
    'test-access-key-002',
    'AES_ENCRYPTED_SECRET_KEY_002',
    'client-002',
    'PREMIUM',
    '*', -- 모든 IP 허용
    DATE_ADD(NOW(), INTERVAL 6 MONTH),
    '개발 테스트용 프리미엄 API Key',
    'ACTIVE',
    0,
    NOW(),
    NOW()
);

-- 만료된 API Key 테스트용
INSERT INTO api_keys (
    access_key,
    secret,
    client_id,
    rate_limit_tier,
    allowed_ips,
    expired_at,
    description,
    status,
    is_deleted,
    created_at,
    updated_at
) VALUES (
    'expired-access-key',
    'AES_ENCRYPTED_SECRET_KEY_EXPIRED',
    'client-003',
    'STANDARD',
    '192.168.1.100',
    DATE_SUB(NOW(), INTERVAL 1 DAY), -- 어제 만료
    '만료 테스트용 API Key',
    'ACTIVE', -- 상태는 ACTIVE 이지만 expired_at 이 과거
    0,
    DATE_SUB(NOW(), INTERVAL 30 DAY),
    NOW()
);

-- 비활성화된 API Key 테스트용
INSERT INTO api_keys (
    access_key,
    secret,
    client_id,
    rate_limit_tier,
    allowed_ips,
    expired_at,
    description,
    status,
    is_deleted,
    created_at,
    updated_at
) VALUES (
    'inactive-access-key',
    'AES_ENCRYPTED_SECRET_KEY_INACTIVE',
    'client-004',
    'BASIC',
    '10.0.0.0/8',
    DATE_ADD(NOW(), INTERVAL 1 YEAR),
    '비활성화 테스트용 API Key',
    'SUSPENDED',
    0,
    DATE_SUB(NOW(), INTERVAL 7 DAY),
    NOW()
);

-- 엔터프라이즈 티어 API Key
INSERT INTO api_keys (
    access_key,
    secret,
    client_id,
    rate_limit_tier,
    allowed_ips,
    expired_at,
    description,
    status,
    is_deleted,
    created_at,
    updated_at
) VALUES (
    'enterprise-access-key',
    'AES_ENCRYPTED_SECRET_KEY_ENTERPRISE',
    'enterprise-client-001',
    'ENTERPRISE',
    '203.0.113.0/24,198.51.100.0/24', -- 특정 네트워크만 허용
    DATE_ADD(NOW(), INTERVAL 2 YEAR),
    '엔터프라이즈 고객용 API Key',
    'ACTIVE',
    0,
    NOW(),
    NOW()
);

-- =============================================================================
-- 2. 소프트 삭제된 API Key (테스트용)
-- =============================================================================

INSERT INTO api_keys (
    access_key,
    secret,
    client_id,
    rate_limit_tier,
    allowed_ips,
    expired_at,
    description,
    status,
    is_deleted,
    deleted_at,
    created_at,
    updated_at
) VALUES (
    'deleted-access-key',
    'AES_ENCRYPTED_SECRET_KEY_DELETED',
    'client-005',
    'STANDARD',
    '192.168.0.0/16',
    DATE_ADD(NOW(), INTERVAL 1 YEAR),
    '소프트 삭제 테스트용 API Key',
    'REVOKED',
    1, -- 삭제됨
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_SUB(NOW(), INTERVAL 10 DAY),
    DATE_SUB(NOW(), INTERVAL 1 DAY)
);

-- =============================================================================
-- 3. 성능 테스트용 더미 데이터 (선택적)
-- =============================================================================

-- 대량 데이터 성능 테스트가 필요한 경우 아래 쿼리 사용
-- 주의: 개발 환경에서만 실행하고, 운영 환경에서는 제외

/*
-- 성능 테스트용 API Key 대량 생성 (100 개)
INSERT INTO api_keys (access_key, secret, client_id, rate_limit_tier, description, status, is_deleted, created_at, updated_at)
SELECT
    CONCAT('test-perf-key-', LPAD(numbers.n, 3, '0')) as access_key,
    CONCAT('AES_ENCRYPTED_SECRET_', LPAD(numbers.n, 3, '0')) as secret,
    CONCAT('perf-client-', LPAD(FLOOR((numbers.n - 1) / 10) + 1, 2, '0')) as client_id,
    ELT(((numbers.n - 1) % 4) + 1, 'BASIC', 'STANDARD', 'PREMIUM', 'ENTERPRISE') as rate_limit_tier,
    CONCAT('성능 테스트용 API Key #', numbers.n) as description,
    'ACTIVE' as status,
    0 as is_deleted,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY) as created_at,
    NOW() as updated_at
FROM (
    SELECT a.N + b.N * 10 + 1 n
    FROM (SELECT 0 N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
    CROSS JOIN (SELECT 0 N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
    WHERE a.N + b.N * 10 + 1 <= 100
) numbers;
*/