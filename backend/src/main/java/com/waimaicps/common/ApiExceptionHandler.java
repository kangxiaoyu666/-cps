package com.waimaicps.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.code()) {
            case "UNAUTHORIZED", "INVALID_CREDENTIALS" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN", "TENANT_CONTEXT_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "ORDER_NOT_FOUND", "WITHDRAWAL_NOT_FOUND", "USER_NOT_FOUND", "TENANT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DUPLICATE_OPERATION", "SETTLEMENT_CONFLICT", "WITHDRAWAL_CONFLICT", "JOB_ALREADY_RUNNING" -> HttpStatus.CONFLICT;
            case "LOGIN_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiResponse<>(ex.code(), ex.getMessage(), null, requestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("参数校验失败");
        return ResponseEntity.badRequest().body(new ApiResponse<>("VALIDATION_ERROR", message, Map.of(), requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>("INTERNAL_ERROR", "服务暂时不可用", null, requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
