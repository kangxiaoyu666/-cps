package com.waimaicps.admin;

import com.waimaicps.affiliate.AffiliateConfigurationService;
import com.waimaicps.affiliate.AffiliateConfigurationService.ConfigurationWrite;
import com.waimaicps.affiliate.AffiliatePlatform;
import com.waimaicps.auth.AdminContext;
import com.waimaicps.common.ApiResponse;
import com.waimaicps.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/affiliate-configurations")
public class AffiliateConfigurationController {
    private final AffiliateConfigurationService configurations;

    public AffiliateConfigurationController(AffiliateConfigurationService configurations) {
        this.configurations = configurations;
    }

    @GetMapping
    ApiResponse<?> list(HttpServletRequest request) {
        long tenantId = AdminContext.requireTenantAdmin().requireTenantId();
        return ok(configurations.list(tenantId), request);
    }

    @GetMapping("/{platform}")
    ApiResponse<?> get(@PathVariable String platform, HttpServletRequest request) {
        long tenantId = AdminContext.requireTenantAdmin().requireTenantId();
        return ok(configurations.get(tenantId, AffiliatePlatform.parse(platform)), request);
    }

    @PutMapping("/{platform}")
    ApiResponse<?> save(
            @PathVariable String platform,
            @RequestBody ConfigurationWrite input,
            HttpServletRequest request) {
        long tenantId = AdminContext.requireTenantAdmin().requireTenantId();
        return ok(configurations.save(tenantId, AffiliatePlatform.parse(platform), input), request);
    }

    @DeleteMapping("/{platform}")
    ApiResponse<?> delete(@PathVariable String platform, HttpServletRequest request) {
        long tenantId = AdminContext.requireTenantAdmin().requireTenantId();
        configurations.delete(tenantId, AffiliatePlatform.parse(platform));
        return ok(java.util.Map.of("deleted", true), request);
    }

    private ApiResponse<?> ok(Object data, HttpServletRequest request) {
        return ApiResponse.success(data, String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }
}
