-- Reserved draft. Active schema is V2__init_schema.sql.
/*
CREATE TABLE tenant (
  id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_code (code), CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE tenant_admin (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, username VARCHAR(64) NOT NULL, password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', version BIGINT NOT NULL DEFAULT 0, last_login_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_tenant_admin_username(username), KEY idx_tenant_admin_tenant(tenant_id), CONSTRAINT fk_tenant_admin_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT ck_tenant_admin_status CHECK(status IN('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE platform_admin (
  id BIGINT NOT NULL AUTO_INCREMENT, username VARCHAR(64) NOT NULL, password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_platform_admin_username(username), CONSTRAINT ck_platform_admin_status CHECK(status IN('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE wx_user (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, openid VARCHAR(128) NOT NULL, unionid VARCHAR(128) NULL, nickname VARCHAR(64) NULL, avatar_url VARCHAR(512) NULL, phone_encrypted VARCHAR(255) NULL, inviter_user_id BIGINT NULL, invite_code VARCHAR(32) NULL, invited_at DATETIME(6) NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_wx_user_openid(tenant_id,openid), UNIQUE KEY uk_wx_user_invite_code(tenant_id,invite_code), KEY idx_wx_user_inviter(tenant_id,inviter_user_id), CONSTRAINT fk_wx_user_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_wx_user_inviter FOREIGN KEY(inviter_user_id) REFERENCES wx_user(id), CONSTRAINT ck_wx_user_not_self CHECK(inviter_user_id IS NULL OR inviter_user_id<>id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE mini_session (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, wx_user_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL, expires_at DATETIME(6) NOT NULL, revoked_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_mini_session_hash(token_hash), KEY idx_mini_session_user(tenant_id,wx_user_id), CONSTRAINT fk_mini_session_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_mini_session_user FOREIGN KEY(wx_user_id) REFERENCES wx_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE affiliate_channel (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, platform VARCHAR(32) NOT NULL, name VARCHAR(128) NOT NULL, config_ciphertext TEXT NULL, status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED', version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_channel(tenant_id,platform,name), CONSTRAINT fk_channel_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT ck_channel_platform CHECK(platform IN('MEITUAN','ELEME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE affiliate_pid (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, channel_id BIGINT NOT NULL, wx_user_id BIGINT NULL, pid VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_pid(tenant_id,channel_id,pid), UNIQUE KEY uk_user_channel_pid(tenant_id,channel_id,wx_user_id), CONSTRAINT fk_pid_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_pid_channel FOREIGN KEY(channel_id) REFERENCES affiliate_channel(id), CONSTRAINT fk_pid_user FOREIGN KEY(wx_user_id) REFERENCES wx_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE affiliate_order (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, wx_user_id BIGINT NULL, pid_id BIGINT NULL, platform VARCHAR(32) NOT NULL, external_order_no VARCHAR(128) NOT NULL, paid_amount_fen BIGINT NOT NULL DEFAULT 0, commission_base_fen BIGINT NOT NULL DEFAULT 0, estimated_commission_fen BIGINT NOT NULL DEFAULT 0, settled_commission_fen BIGINT NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL, ordered_at DATETIME(6) NOT NULL, settled_at DATETIME(6) NULL, raw_payload JSON NULL, version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_affiliate_order(tenant_id,platform,external_order_no), KEY idx_order_user_time(tenant_id,wx_user_id,ordered_at), CONSTRAINT fk_order_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_order_user FOREIGN KEY(wx_user_id) REFERENCES wx_user(id), CONSTRAINT fk_order_pid FOREIGN KEY(pid_id) REFERENCES affiliate_pid(id), CONSTRAINT ck_order_amount CHECK(paid_amount_fen>=0 AND commission_base_fen>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE commission_rule (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, name VARCHAR(128) NOT NULL, platform VARCHAR(32) NULL, direct_rate_bp INT NOT NULL, inviter_rate_bp INT NOT NULL DEFAULT 0, effective_from DATETIME(6) NOT NULL, effective_to DATETIME(6) NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), KEY idx_rule_effective(tenant_id,status,effective_from), CONSTRAINT fk_rule_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT ck_rule_rates CHECK(direct_rate_bp BETWEEN 0 AND 10000 AND inviter_rate_bp BETWEEN 0 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE commission_record (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, order_id BIGINT NOT NULL, beneficiary_user_id BIGINT NOT NULL, rule_id BIGINT NULL, record_type VARCHAR(32) NOT NULL, amount_fen BIGINT NOT NULL, status VARCHAR(32) NOT NULL, reversed_record_id BIGINT NULL, version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_commission_idempotency(tenant_id,order_id,beneficiary_user_id,record_type), CONSTRAINT fk_commission_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_commission_order FOREIGN KEY(order_id) REFERENCES affiliate_order(id), CONSTRAINT fk_commission_user FOREIGN KEY(beneficiary_user_id) REFERENCES wx_user(id), CONSTRAINT fk_commission_rule FOREIGN KEY(rule_id) REFERENCES commission_rule(id), CONSTRAINT fk_commission_reverse FOREIGN KEY(reversed_record_id) REFERENCES commission_record(id), CONSTRAINT ck_commission_amount CHECK(amount_fen>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE wallet_account (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, wx_user_id BIGINT NOT NULL, available_fen BIGINT NOT NULL DEFAULT 0, frozen_fen BIGINT NOT NULL DEFAULT 0, total_earned_fen BIGINT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_wallet_user(tenant_id,wx_user_id), CONSTRAINT fk_wallet_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_wallet_user FOREIGN KEY(wx_user_id) REFERENCES wx_user(id), CONSTRAINT ck_wallet_balance CHECK(available_fen>=0 AND frozen_fen>=0 AND total_earned_fen>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE wallet_entry (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, wallet_account_id BIGINT NOT NULL, entry_type VARCHAR(32) NOT NULL, amount_fen BIGINT NOT NULL, business_type VARCHAR(32) NOT NULL, business_id VARCHAR(128) NOT NULL, created_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_wallet_entry_business(tenant_id,wallet_account_id,business_type,business_id,entry_type), KEY idx_wallet_entry_time(tenant_id,wallet_account_id,created_at), CONSTRAINT fk_entry_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_entry_wallet FOREIGN KEY(wallet_account_id) REFERENCES wallet_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE withdrawal (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, wx_user_id BIGINT NOT NULL, amount_fen BIGINT NOT NULL, status VARCHAR(32) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, offline_payment_reference VARCHAR(128) NULL, version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_withdrawal_idempotency(tenant_id,idempotency_key), KEY idx_withdrawal_status(tenant_id,status,created_at), CONSTRAINT fk_withdrawal_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_withdrawal_user FOREIGN KEY(wx_user_id) REFERENCES wx_user(id), CONSTRAINT ck_withdrawal_amount CHECK(amount_fen>0), CONSTRAINT ck_withdrawal_status CHECK(status IN('SUBMITTED','APPROVED','REJECTED','CANCELLED','PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE withdrawal_audit (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, withdrawal_id BIGINT NOT NULL, admin_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL, note VARCHAR(512) NOT NULL, created_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), KEY idx_withdrawal_audit(tenant_id,withdrawal_id,created_at), CONSTRAINT fk_wa_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id), CONSTRAINT fk_wa_withdrawal FOREIGN KEY(withdrawal_id) REFERENCES withdrawal(id), CONSTRAINT fk_wa_admin FOREIGN KEY(admin_id) REFERENCES tenant_admin(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE content_config (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, config_key VARCHAR(128) NOT NULL, content_json JSON NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', version BIGINT NOT NULL DEFAULT 0, published_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_content_key(tenant_id,config_key), CONSTRAINT fk_content_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE job_execution (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NULL, job_name VARCHAR(128) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, started_at DATETIME(6) NOT NULL, finished_at DATETIME(6) NULL, detail_json JSON NULL, version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY(id), UNIQUE KEY uk_job_execution(job_name,idempotency_key), KEY idx_job_tenant(tenant_id,started_at), CONSTRAINT fk_job_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NULL, actor_type VARCHAR(32) NOT NULL, actor_id BIGINT NULL, action VARCHAR(128) NOT NULL, resource_type VARCHAR(64) NOT NULL, resource_id VARCHAR(128) NULL, request_id VARCHAR(64) NOT NULL, ip_address VARCHAR(64) NULL, detail_json JSON NULL, created_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), KEY idx_audit_tenant_time(tenant_id,created_at), KEY idx_audit_request(request_id),   CONSTRAINT fk_audit_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
*/
