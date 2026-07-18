-- Phase 4 Step 4.5: 대량 복호 3단계 승인 상태머신.
--
-- 배경: Step 4.2(EnvelopeCrypto)가 만든 엔벨로프 암호문을 대량으로 복호(예: 감사/수사
-- 목적의 일괄 조회)하려면, "요청 -> 승인/거부 -> (승인된 것만) 실제 복호 실행" 게이팅이
-- 필요하다(deliverable.md 설계 배경). 이 테이블은 그 요청/결정 이력을 담는다.
--
-- 상태 전이 규칙(애플리케이션 계층인 com.hospitalops.approval.BulkDecryptionRequest가
-- 강제): PENDING -> APPROVED 또는 PENDING -> REJECTED로만 가능하고, 그 반대나
-- APPROVED/REJECTED에서 다른 상태로의 전이는 전부 불가(역행 불가). CHECK 제약은 값
-- 자체가 세 값 중 하나인지만 DB 레벨에서 방어하고, "PENDING에서만 전이 가능"이라는
-- 전이 규칙 자체는 애플리케이션 계층의 책임이다(DB 트리거까지는 과한 엔지니어링으로
-- 판단 - 이 프로젝트의 다른 상태 관리도 애플리케이션 계층에서 강제하는 관례를 따른다).
CREATE TABLE BULK_DECRYPTION_REQUEST (
    request_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    requested_by    VARCHAR(50)  NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    requested_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by      VARCHAR(50)  NULL,
    decided_at      DATETIME     NULL,
    decision_note   VARCHAR(500) NULL,
    CONSTRAINT chk_bulk_decryption_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_bulk_decryption_status ON BULK_DECRYPTION_REQUEST (status);
CREATE INDEX idx_bulk_decryption_requested_by ON BULK_DECRYPTION_REQUEST (requested_by);
