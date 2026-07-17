ALTER TABLE affiliate_channel
  MODIFY encrypted_config MEDIUMBLOB NULL,
  ADD COLUMN configured_at TIMESTAMP(6) NULL AFTER last_validation_result;

CREATE TABLE affiliate_attribution (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  channel_id BIGINT UNSIGNED NOT NULL,
  platform VARCHAR(32) NOT NULL,
  tracking_id VARCHAR(64) NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_affiliate_attribution_tracking (tenant_id, platform, tracking_id),
  KEY idx_affiliate_attribution_user (tenant_id, user_id),
  CONSTRAINT fk_affiliate_attribution_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_affiliate_attribution_channel FOREIGN KEY (channel_id) REFERENCES affiliate_channel(id),
  CONSTRAINT fk_affiliate_attribution_user FOREIGN KEY (user_id) REFERENCES wx_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE affiliate_sync_cursor (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  platform VARCHAR(32) NOT NULL,
  cursor_value VARCHAR(512) NULL,
  window_start TIMESTAMP(6) NULL,
  window_end TIMESTAMP(6) NULL,
  last_success_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_affiliate_sync_cursor (tenant_id, platform),
  CONSTRAINT fk_affiliate_sync_cursor_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE affiliate_order
  ADD COLUMN refunded_commission_cent BIGINT NOT NULL DEFAULT 0 AFTER settled_commission_cent,
  ADD COLUMN refund_processed_at TIMESTAMP(6) NULL AFTER commission_processed_at,
  ADD CONSTRAINT chk_affiliate_order_refund CHECK (refunded_commission_cent >= 0);

ALTER TABLE wallet_account DROP CHECK chk_wallet_non_negative;
ALTER TABLE wallet_entry DROP CHECK chk_wallet_entry_balance;
