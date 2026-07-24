package com.waimaicps.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class AdminAuthenticationFilter extends OncePerRequestFilter {
    public static final String SESSION_ATTRIBUTE = "ADMIN_PRINCIPAL";
    private final JdbcTemplate jdbc;

    public AdminAuthenticationFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/platform/"))
                || path.equals("/api/v1/admin/auth/login")
                || path.equals("/api/v1/platform/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof AdminPrincipal principal) {
            if (isActive(principal)) {
                AdminContext.set(principal);
            } else {
                session.invalidate();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            AdminContext.clear();
        }
    }

    private boolean isActive(AdminPrincipal principal) {
        if (principal.role() == AdminPrincipal.Role.PLATFORM_ADMIN) {
            return count(
                    "SELECT COUNT(*) FROM platform_admin WHERE id=? AND status='ACTIVE'",
                    principal.adminId()) == 1;
        }
        return principal.tenantId() != null
                && count(
                        "SELECT COUNT(*) FROM tenant_admin a JOIN tenant t ON t.id=a.tenant_id "
                                + "WHERE a.id=? AND a.tenant_id=? AND a.status='ACTIVE' AND t.status='ACTIVE'",
                        principal.adminId(), principal.tenantId()) == 1;
    }

    private int count(String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count == null ? 0 : count;
    }
}
