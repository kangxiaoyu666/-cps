# 多租户隔离

- 小程序 tenantId/userId 来自服务端随机令牌；后台 tenantId 来自 HttpOnly Cookie 对应的登录身份。
- Controller 与 DTO 不接受 tenantId/openid 作为授权参数。
- Repository 方法必须以 tenantId 作为首个条件；联合唯一索引都包含 tenant_id。
- 平台管理员跨租户入口与租户入口分离，并要求独立权限和审计。
- 集成测试使用两个租户互相访问用户、订单、钱包、提现，必须返回无权限或不存在。
