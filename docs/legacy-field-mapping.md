# 旧数据库字段映射（仅设计，不执行）

| 旧表/字段 | 新表/字段 | 处理 |
|---|---|---|
| user.openid | wx_user.openid_ciphertext/openid_hash | 加密与哈希，不明文迁移 |
| user.parent_id | wx_user.direct_inviter_id | 校验自邀请与关系环后导入 |
| pid.pid/sid/relation_id | affiliate_pid | 按租户与官方渠道重建 |
| order.order_id | affiliate_order.external_order_id | 与 tenant+platform 建唯一索引 |
| order.c_1 | commission_record SELF_PURCHASE | 仅审计映射，不直接入账 |
| order.c_2 | commission_record DIRECT_INVITE | 仅保留一级 |
| order.c_3/c_t | 无 | 不迁移二级与团长佣金 |
| user.money | wallet_account | 禁止直接迁移余额；须审计对账后开账 |
| cash | withdrawal | 状态人工核验后迁移历史记录 |
| *_u | 无 | 不迁移镜像表设计 |

MVP 不执行真实迁移，任何资金导入必须有审计签字和对账报告。
