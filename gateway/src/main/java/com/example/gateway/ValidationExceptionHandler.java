package com.example.gateway;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolationException;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
@Order(0)
public class ValidationExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleBodyValidation(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(buildErrorResponse("ERR_VALIDATION", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleHeaderValidation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(buildErrorResponse("ERR_VALIDATION", message));
    }

    private String formatFieldError(FieldError error) {
        if (error.getDefaultMessage() == null) {
            return error.getField() + " invalid";
        }
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private Map<String, Object> buildErrorResponse(String code, String message) {
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("responseDate", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        head.put("responseTime", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("errCode", code);
        error.put("errMessage", message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("head", head);
        response.put("error", error);
        return response;
    }
}
