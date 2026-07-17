package com.waimaicps.affiliate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AffiliateAdapter {
    PromotionLink generatePromotionLink(long tenantId, long userId, String activityCode);
    OrderPage fetchOrderPage(long tenantId, Instant from, Instant to, String cursor);
    default List<AffiliateOrderPayload> fetchOrders(long tenantId, Instant from, Instant to, String cursor) {
        return fetchOrderPage(tenantId, from, to, cursor).orders();
    }
    AffiliateOrderPayload queryOrder(long tenantId, String externalOrderId);
    ValidationResult validateConfiguration(long tenantId);

    record PromotionLink(String platform, String url, Instant expiresAt) {}
    record OrderPage(List<AffiliateOrderPayload> orders, String nextCursor, boolean hasMore) {}
    record AffiliateOrderPayload(String externalOrderId, String status, long amountCent,
            long commissionCent, long refundedCommissionCent, Instant paidAt, Instant refundedAt,
            String trackingId, Map<String, Object> maskedSnapshot) {}
    record ValidationResult(boolean valid, String code, String message) {}
}
