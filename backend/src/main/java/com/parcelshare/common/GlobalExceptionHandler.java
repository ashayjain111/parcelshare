package com.parcelshare.common;

import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Malformed request body"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(Exception e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "That file is too large (max 4 MB)"));
    }

    @ExceptionHandler(org.springframework.beans.TypeMismatchException.class)
    ResponseEntity<Map<String, String>> mismatch(org.springframework.beans.TypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid value for " + e.getPropertyName()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> locking(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "This booking was just changed by someone else. Please refresh and retry."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Request conflicts with existing data"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> other(Exception e) {
        // Framework errors that already carry a proper status (404 unknown route, 405 wrong method, 415, ...)
        if (e instanceof org.springframework.web.ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(er.getStatusCode()).body(Map.of("error", "Request not supported"));
        }
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Something went wrong"));
    }
}
