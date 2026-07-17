# 统一错误码

| code | 含义 |
|---|---|
| SUCCESS | 成功 |
| VALIDATION_ERROR | 参数错误 |
| UNAUTHORIZED / FORBIDDEN | 未登录或无权操作 |
| AFFILIATE_NOT_CONFIGURED | 官方联盟凭据未配置 |
| ORDER_NOT_SETTLED | 订单尚未结算 |
| DUPLICATE_OPERATION | 幂等操作已执行 |
| INSUFFICIENT_BALANCE | 可用或冻结余额不足 |
| WITHDRAWAL_BELOW_MINIMUM | 低于提现门槛 |
| INVALID_WITHDRAWAL_TRANSITION | 非法状态迁移 |
| WITHDRAWAL_CONFLICT | 并发导致状态已变化 |
| INTERNAL_ERROR | 未预期服务错误，不泄露内部细节 |
