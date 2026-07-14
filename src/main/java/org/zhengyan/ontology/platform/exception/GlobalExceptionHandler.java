package org.zhengyan.ontology.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String KEY_ERROR = "error";
    private static final String KEY_STATUS = "status";
    private static final String KEY_MESSAGE = "message";

    @ExceptionHandler(OntologyPlatformException.class)
    public ResponseEntity<Map<String, Object>> handlePlatformException(OntologyPlatformException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(KEY_ERROR, ex.getErrorCode());
        body.put(KEY_MESSAGE, ex.getMessage());
        body.put(KEY_STATUS, ex.getHttpStatus());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(KEY_ERROR, "BAD_REQUEST");
        body.put(KEY_MESSAGE, ex.getMessage());
        body.put(KEY_STATUS, 400);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(KEY_ERROR, "FORBIDDEN");
        body.put(KEY_MESSAGE, "Insufficient permissions");
        body.put(KEY_STATUS, 403);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) throws Exception {
        if (ex instanceof NoResourceFoundException) {
            throw ex;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(KEY_ERROR, "INTERNAL_ERROR");
        body.put(KEY_MESSAGE, ex.getMessage());
        body.put(KEY_STATUS, 500);
        return ResponseEntity.internalServerError().body(body);
    }
}
