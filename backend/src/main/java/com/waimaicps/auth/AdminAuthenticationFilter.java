package com.waimaicps.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class AdminAuthenticationFilter extends OncePerRequestFilter {
    public static final String SESSION_ATTRIBUTE = "ADMIN_PRINCIPAL";

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
        if (value instanceof AdminPrincipal principal) AdminContext.set(principal);
        try {
            chain.doFilter(request, response);
        } finally {
            AdminContext.clear();
        }
    }
}
