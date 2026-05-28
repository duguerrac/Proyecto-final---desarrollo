package com.smartlogistics.warehouse.infrastructure.adapter.in.rest;

import com.smartlogistics.warehouse.domain.exception.*;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({BusinessException.class, RobotRejectedException.class, RoutePlanningException.class, InsufficientStockException.class})
    public ResponseEntity<ApiError> conflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request body");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }

    public record ApiError(Instant timestamp, int status, String error, String message) {
    }
}
