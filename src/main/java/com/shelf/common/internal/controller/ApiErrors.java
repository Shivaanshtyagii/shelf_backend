package com.shelf.common.internal.controller;

import com.shelf.common.ApiFault;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiFault.class)
    ProblemDetail fault(ApiFault fault) {
        return ProblemDetail.forStatusAndDetail(fault.status(), fault.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        var field = exception.getBindingResult().getFieldError();
        var message = field == null ? "Check the submitted fields." : "Check the value for " + field.getField() + ".";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This change conflicts with an existing record.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The file exceeds the maximum upload size.");
    }
}
