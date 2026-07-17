# 字段字典

| 表 | 关键字段 | 说明 |
|---|---|---|
| tenant | id, code, status | 租户与品牌 |
| wx_user | tenant_id, openid_hash, direct_inviter_id | 微信身份密文/哈希与不可随意修改的直接邀请关系 |
| affiliate_channel | encrypted_config, platform | 租户独立官方联盟配置 |
| affiliate_order | external_order_id, status, *_cent, rule_*_bps | 订单、金额和分佣快照 |
| commission_record | beneficiary_user_id, reward_type, amount_cent | 单订单单受益人单奖励类型唯一 |
| wallet_account | available_cent, frozen_cent, version | 钱包汇总，只由账本服务更新 |
| wallet_entry | business_no, before/after, idempotency_key | 不可修改资金流水 |
| withdrawal | status, amount_cent, payout_reference, version | 人工提现状态机 |
| audit_log | actor, resource, request_id | 高权限操作审计 |

所有金额单位为分，时间使用 UTC，所有租户业务表含 tenant_id。完整 DDL 以 Flyway 为准。
