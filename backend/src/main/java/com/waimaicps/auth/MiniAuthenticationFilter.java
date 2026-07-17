package com.waimaicps.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.common.ApiResponse;
import com.waimaicps.common.BusinessException;
import com.waimaicps.common.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MiniAuthenticationFilter extends OncePerRequestFilter {
    private final MiniSessionService sessions;
    private final ObjectMapper objectMapper;

    public MiniAuthenticationFilter(MiniSessionService sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/mini/") || path.equals("/api/v1/mini/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        try {
            TenantContext.set(sessions.authenticate(token));
            chain.doFilter(request, response);
        } catch (BusinessException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), new ApiResponse<>(ex.code(), ex.getMessage(), null,
                    String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE))));
        } finally {
            TenantContext.clear();
        }
    }
}
