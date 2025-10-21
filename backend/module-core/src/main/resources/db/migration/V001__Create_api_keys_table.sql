-- API Keys 테이블 생성
-- Created: 2024-09-23
-- Description: API Key 관리를 위한 테이블 생성 및 기본 인덱스 설정

CREATE TABLE IF NOT EXISTS api_keys (
    -- 기본 필드 (BaseEntity)
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'API Key 고유 ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',

    -- 소프트 삭제 필드 (SoftDeleteEntity)
    deleted_at TIMESTAMP NULL COMMENT '삭제 시간',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '삭제 여부 (0: 활성, 1: 삭제)',

    -- API Key 핵심 필드
    access_key VARCHAR(64) NOT NULL COMMENT 'API Access Key (공개키)',
    secret VARCHAR(512) NOT NULL COMMENT '암호화된 Secret Key',
    client_id VARCHAR(64) NOT NULL COMMENT '클라이언트 ID',

    -- Rate Limiting 관련
    rate_limit_tier VARCHAR(20) DEFAULT 'BASIC' COMMENT 'Rate Limit 티어 (BASIC, STANDARD, PREMIUM, ENTERPRISE)',

    -- 보안 관련
    allowed_ips TEXT COMMENT '허용된 IP 주소 목록 (쉼표 구분)',

    -- 만료 관리
    expired_at TIMESTAMP NULL COMMENT 'API Key 만료 시간',

    -- 부가 정보
    description VARCHAR(500) COMMENT 'API Key 설명',

    -- 상태 관리
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'API Key 상태 (ACTIVE, SUSPENDED, EXPIRED, REVOKED)'

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci
  COMMENT='API Key 관리 테이블';