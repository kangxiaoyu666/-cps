ALTER TABLE wallet_account
  ADD COLUMN debt_cent BIGINT NOT NULL DEFAULT 0 AFTER frozen_cent;

ALTER TABLE wallet_entry
  ADD COLUMN debt_delta_cent BIGINT NOT NULL DEFAULT 0 AFTER frozen_delta_cent,
  ADD COLUMN debt_before_cent BIGINT NOT NULL DEFAULT 0 AFTER frozen_after_cent,
  ADD COLUMN debt_after_cent BIGINT NOT NULL DEFAULT 0 AFTER debt_before_cent;

UPDATE wallet_account
SET debt_cent = GREATEST(-available_cent, 0),
    available_cent = GREATEST(available_cent, 0);

UPDATE wallet_entry
SET debt_before_cent = GREATEST(-available_before_cent, 0),
    debt_after_cent = GREATEST(-available_after_cent, 0),
    debt_delta_cent = GREATEST(-available_after_cent, 0) - GREATEST(-available_before_cent, 0),
    available_before_cent = GREATEST(available_before_cent, 0),
    available_after_cent = GREATEST(available_after_cent, 0);

ALTER TABLE wallet_account
  ADD CONSTRAINT chk_wallet_balances_non_negative
    CHECK (available_cent >= 0 AND frozen_cent >= 0 AND debt_cent >= 0 AND lifetime_income_cent >= 0);

ALTER TABLE wallet_entry
  ADD CONSTRAINT chk_wallet_entry_balances_non_negative
    CHECK (available_after_cent >= 0 AND frozen_after_cent >= 0 AND debt_after_cent >= 0);
