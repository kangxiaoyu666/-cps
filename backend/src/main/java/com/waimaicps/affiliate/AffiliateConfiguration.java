package com.waimaicps.affiliate;

import java.util.Set;

public record AffiliateConfiguration(
        String appKey,
        String appSecret,
        String activityId,
        String pid,
        String sid,
        Set<String> permissions) {

    public AffiliateConfiguration {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
