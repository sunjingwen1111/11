package com.example.demo.system.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = UserController.class)
public class UserExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        // 不返回或记录 rejectedValue，避免校验失败时泄露密码。
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct().collect(java.util.stream.Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ApiError(400, message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badRequest() {
        return ResponseEntity.badRequest().body(new ApiError(400, "请求格式或参数类型错误"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> notFound(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ApiError(exception.getStatusCode().value(), exception.getReason()));
    }

    public record ApiError(int status, String message) {
    }
}
