package com.shelf.api.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException ex) {
        return ApiError.of(ex.status(), ex.status().name(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthorized() {
        return ApiError.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in to continue.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden() {
        return ApiError.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict() {
        return ApiError.of(HttpStatus.CONFLICT, "DATA_CONFLICT", "This change conflicts with an existing record.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                              HttpStatusCode status, WebRequest request) {
        String detail = switch (ex) {
            case MethodArgumentNotValidException invalid -> {
                var field = invalid.getBindingResult().getFieldError();
                yield field == null ? "Check the submitted fields." : "Check the value for " + field.getField() + ".";
            }
            case MaxUploadSizeExceededException ignored -> "The file exceeds the maximum upload size.";
            default -> HttpStatus.valueOf(status.value()).getReasonPhrase();
        };
        var problem = ApiError.of(status, HttpStatus.valueOf(status.value()).name(), detail);
        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        LOG.error("Unhandled request failure", ex);
        return ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed.");
    }
}
