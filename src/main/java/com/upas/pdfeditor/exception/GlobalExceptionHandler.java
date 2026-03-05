package com.upas.pdfeditor.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<Map<String, Object>> handlePdfError(PdfProcessingException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("status", "ERROR");
        error.put("message", ex.getMessage());

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralError(Exception ex) {

        log.error("Unexpected error occurred", ex);

        Map<String, Object> error = new HashMap<>();
        error.put("status", "ERROR");
        error.put("message", ex.getMessage());   // return actual error

        return ResponseEntity.internalServerError().body(error);
    }
}