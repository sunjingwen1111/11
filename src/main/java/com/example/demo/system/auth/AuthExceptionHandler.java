package com.example.demo.system.auth;

import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiError> notLoggedIn() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(401, "请先登录"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> duplicateAccount() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(409, "账号已存在"));
    }

    @RestControllerAdvice(assignableTypes = AuthController.class)
    public static class AuthRequestErrors {
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> invalidFields(MethodArgumentNotValidException exception) {
            String message = exception.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .distinct().collect(Collectors.joining("; "));
            return ResponseEntity.badRequest().body(new ApiError(400, message));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiError> invalidBody() {
            return ResponseEntity.badRequest().body(new ApiError(400, "请求格式错误"));
        }

        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiError> knownError(ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(new ApiError(exception.getStatusCode().value(), exception.getReason()));
        }
    }

    public record ApiError(int status, String message) {
    }
}
