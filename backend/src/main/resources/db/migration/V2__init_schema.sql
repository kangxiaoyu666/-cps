CREATE TABLE tenant (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  brand_name VARCHAR(128) NULL,
  brand_logo_url VARCHAR(512) NULL,
  support_contact VARCHAR(255) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE platform_admin (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_login_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_platform_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_admin (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  role_code VARCHAR(64) NOT NULL DEFAULT 'TENANT_ADMIN',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_login_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_admin_username (tenant_id, username),
  CONSTRAINT fk_tenant_admin_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wx_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  openid_ciphertext VARBINARY(512) NOT NULL,
  openid_hash BINARY(32) NOT NULL,
  unionid_ciphertext VARBINARY(512) NULL,
  nickname VARCHAR(128) NULL,
  avatar_url VARCHAR(512) NULL,
  direct_inviter_id BIGINT UNSIGNED NULL,
  invite_code VARCHAR(32) NOT NULL,
  real_name_ciphertext VARBINARY(512) NULL,
  mobile_ciphertext VARBINARY(512) NULL,
  payout_account_ciphertext VARBINARY(1024) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  invited_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wx_user_openid (tenant_id, openid_hash),
  UNIQUE KEY uk_wx_user_invite (tenant_id, invite_code),
  KEY idx_wx_user_inviter (tenant_id, direct_inviter_id),
  CONSTRAINT fk_wx_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_wx_user_inviter FOREIGN KEY (direct_inviter_id) REFERENCES wx_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mini_session (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash BINARY(32) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  last_seen_at TIMESTAMP(6) NOT NULL,
  revoked_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), UNIQUE KEY uk_mini_session_token (token_hash),
  KEY idx_mini_session_user (tenant_id, user_id, expires_at),
  CONSTRAINT fk_mini_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_mini_session_user FOREIGN KEY (user_id) REFERENCES wx_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE affiliate_channel (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  platform VARCHAR(32) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  encrypted_config MEDIUMBLOB NOT NULL,
  config_key_version VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  last_validated_at TIMESTAMP(6) NULL,
  last_validation_result VARCHAR(512) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_affiliate_channel (tenant_id, platform),
  CONSTRAINT fk_affiliate_channel_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE affiliate_pid (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  channel_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NULL,
  external_pid VARCHAR(128) NOT NULL,
  external_sid VARCHAR(128) NULL,
  relation_id VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  bound_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_affiliate_pid_external (tenant_id, channel_id, external_pid),
  KEY idx_affiliate_pid_user (tenant_id, user_id),
  CONSTRAINT fk_affiliate_pid_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_affiliate_pid_channel FOREIGN KEY (channel_id) REFERENCES affiliate_channel(id),
  CONSTRAINT fk_affiliate_pid_user FOREIGN KEY (user_id) REFERENCES wx_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE affiliate_order (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  platform VARCHAR(32) NOT NULL,
  external_order_id VARCHAR(128) NOT NULL,
  channel_id BIGINT UNSIGNED NOT NULL,
  pid_id BIGINT UNSIGNED NULL,
  attributed_user_id BIGINT UNSIGNED NULL,
  status VARCHAR(32) NOT NULL,
  order_amount_cent BIGINT NOT NULL DEFAULT 0,
  estimated_commission_cent BIGINT NOT NULL DEFAULT 0,
  settled_commission_cent BIGINT NOT NULL DEFAULT 0,
  paid_at TIMESTAMP(6) NULL,
  settled_at TIMESTAMP(6) NULL,
  refunded_at TIMESTAMP(6) NULL,
  raw_snapshot_json JSON NULL,
  rule_self_rate_bps INT NOT NULL DEFAULT 0,
  rule_direct_rate_bps INT NOT NULL DEFAULT 0,
  commission_processed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_affiliate_order_external (tenant_id, platform, external_order_id),
  KEY idx_affiliate_order_user (tenant_id, attributed_user_id, paid_at),
  KEY idx_affiliate_order_status (tenant_id, platform, status, updated_at),
  CONSTRAINT fk_affiliate_order_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_affiliate_order_channel FOREIGN KEY (channel_id) REFERENCES affiliate_channel(id),
  CONSTRAINT fk_affiliate_order_pid FOREIGN KEY (pid_id) REFERENCES affiliate_pid(id),
  CONSTRAINT fk_affiliate_order_user FOREIGN KEY (attributed_user_id) REFERENCES wx_user(id),
  CONSTRAINT chk_affiliate_order_amount CHECK (order_amount_cent >= 0 AND estimated_commission_cent >= 0 AND settled_commission_cent >= 0),
  CONSTRAINT chk_affiliate_order_rate CHECK (rule_self_rate_bps BETWEEN 0 AND 10000 AND rule_direct_rate_bps BETWEEN 0 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commission_rule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  self_rate_bps INT NOT NULL DEFAULT 0,
  direct_invite_rate_bps INT NOT NULL DEFAULT 0,
  effective_from TIMESTAMP(6) NOT NULL,
  effective_to TIMESTAMP(6) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_commission_rule_effective (tenant_id, effective_from, effective_to),
  CONSTRAINT fk_commission_rule_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT chk_commission_rule_rate CHECK (self_rate_bps BETWEEN 0 AND 10000 AND direct_invite_rate_bps BETWEEN 0 AND 10000 AND self_rate_bps + direct_invite_rate_bps <= 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commission_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  beneficiary_user_id BIGINT UNSIGNED NOT NULL,
  reward_type VARCHAR(32) NOT NULL,
  rate_bps_snapshot INT NOT NULL,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CREDITED',
  reversal_of_id BIGINT UNSIGNED NULL,
  business_no VARCHAR(128) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_commission_once (tenant_id, order_id, beneficiary_user_id, reward_type),
  UNIQUE KEY uk_commission_business_no (tenant_id, business_no),
  CONSTRAINT fk_commission_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_commission_order FOREIGN KEY (order_id) REFERENCES affiliate_order(id),
  CONSTRAINT fk_commission_user FOREIGN KEY (beneficiary_user_id) REFERENCES wx_user(id),
  CONSTRAINT fk_commission_reversal FOREIGN KEY (reversal_of_id) REFERENCES commission_record(id),
  CONSTRAINT chk_commission_amount CHECK (amount_cent <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_account (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  available_cent BIGINT NOT NULL DEFAULT 0,
  frozen_cent BIGINT NOT NULL DEFAULT 0,
  lifetime_income_cent BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_wallet_user (tenant_id, user_id),
  CONSTRAINT fk_wallet_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES wx_user(id),
  CONSTRAINT chk_wallet_non_negative CHECK (available_cent >= 0 AND frozen_cent >= 0 AND lifetime_income_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_entry (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  wallet_account_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  business_no VARCHAR(128) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  available_delta_cent BIGINT NOT NULL DEFAULT 0,
  frozen_delta_cent BIGINT NOT NULL DEFAULT 0,
  available_before_cent BIGINT NOT NULL,
  available_after_cent BIGINT NOT NULL,
  frozen_before_cent BIGINT NOT NULL,
  frozen_after_cent BIGINT NOT NULL,
  related_order_id BIGINT UNSIGNED NULL,
  related_withdrawal_id BIGINT UNSIGNED NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  memo VARCHAR(512) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_wallet_entry_idempotency (tenant_id, idempotency_key),
  UNIQUE KEY uk_wallet_entry_business (tenant_id, business_type, business_no),
  KEY idx_wallet_entry_user (tenant_id, user_id, created_at),
  CONSTRAINT fk_wallet_entry_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_wallet_entry_account FOREIGN KEY (wallet_account_id) REFERENCES wallet_account(id),
  CONSTRAINT fk_wallet_entry_user FOREIGN KEY (user_id) REFERENCES wx_user(id),
  CONSTRAINT fk_wallet_entry_order FOREIGN KEY (related_order_id) REFERENCES affiliate_order(id),
  CONSTRAINT chk_wallet_entry_balance CHECK (available_after_cent >= 0 AND frozen_after_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE withdrawal (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  withdrawal_no VARCHAR(64) NOT NULL,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  payout_channel VARCHAR(32) NULL,
  payout_reference VARCHAR(128) NULL,
  proof_url VARCHAR(512) NULL,
  rejection_reason VARCHAR(512) NULL,
  submitted_at TIMESTAMP(6) NOT NULL,
  approved_at TIMESTAMP(6) NULL,
  paid_at TIMESTAMP(6) NULL,
  canceled_at TIMESTAMP(6) NULL,
  rejected_at TIMESTAMP(6) NULL,
  reviewed_by BIGINT UNSIGNED NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_withdrawal_no (tenant_id, withdrawal_no),
  UNIQUE KEY uk_withdrawal_idempotency (tenant_id, user_id, idempotency_key),
  KEY idx_withdrawal_status (tenant_id, status, submitted_at),
  CONSTRAINT fk_withdrawal_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_withdrawal_user FOREIGN KEY (user_id) REFERENCES wx_user(id),
  CONSTRAINT chk_withdrawal_amount CHECK (amount_cent > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE wallet_entry ADD CONSTRAINT fk_wallet_entry_withdrawal FOREIGN KEY (related_withdrawal_id) REFERENCES withdrawal(id);

CREATE TABLE withdrawal_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  withdrawal_id BIGINT UNSIGNED NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(32) NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  detail_json JSON NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), KEY idx_withdrawal_audit (tenant_id, withdrawal_id, created_at),
  CONSTRAINT fk_withdrawal_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_withdrawal_audit_withdrawal FOREIGN KEY (withdrawal_id) REFERENCES withdrawal(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_config (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  config_type VARCHAR(64) NOT NULL,
  config_key VARCHAR(128) NOT NULL,
  content_json JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  published_at TIMESTAMP(6) NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_content_config (tenant_id, config_type, config_key),
  CONSTRAINT fk_content_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_execution (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  platform VARCHAR(32) NULL,
  job_type VARCHAR(64) NOT NULL,
  execution_key VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  started_at TIMESTAMP(6) NOT NULL,
  finished_at TIMESTAMP(6) NULL,
  scanned_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), UNIQUE KEY uk_job_execution (tenant_id, execution_key),
  KEY idx_job_execution_status (tenant_id, job_type, status, started_at),
  CONSTRAINT fk_job_execution_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NULL,
  actor_type VARCHAR(32) NOT NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(128) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NULL,
  request_id VARCHAR(64) NOT NULL,
  ip_address VARCHAR(64) NULL,
  detail_json JSON NULL,
  result VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), KEY idx_audit_log_tenant (tenant_id, created_at), KEY idx_audit_log_actor (actor_type, actor_id, created_at),
  CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
