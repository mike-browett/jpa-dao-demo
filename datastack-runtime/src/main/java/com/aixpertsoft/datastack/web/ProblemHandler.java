package com.aixpertsoft.datastack.web;

import com.aixpertsoft.datastack.dao.NotFoundException;
import com.aixpertsoft.datastack.rules.RuleViolation;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns exceptions into application/problem+json responses that always carry a stable "code". */
@RestControllerAdvice
public class ProblemHandler {

    @ExceptionHandler(RuleViolation.class)
    ProblemDetail ruleViolation(RuleViolation e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, e.code(), e.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return problem(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "notFound", e.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    ProblemDetail concurrent(Exception e) {
        return problem(HttpStatus.CONFLICT, "concurrentModification",
                "The object was changed by someone else; reload it and try again");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return problem(HttpStatus.BAD_REQUEST, "validation", message);
    }

    /** Database constraints are the last line of defence; map the known ones to readable codes. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        if (detail.contains("uq_rack_administrator")) {
            return problem(HttpStatus.CONFLICT, "dcim.duplicateAdministrator",
                    "This person already administers this rack");
        }
        if (detail.contains("ex_rack_primary_administrator")) {
            return problem(HttpStatus.CONFLICT, "dcim.duplicatePrimary",
                    "A rack can have only one primary administrator");
        }
        if (detail.contains("serial_number")) {
            return problem(HttpStatus.CONFLICT, "duplicateSerialNumber",
                    "Another component already has this serial number");
        }
        return problem(HttpStatus.CONFLICT, "constraintViolation", "The change violates a database constraint");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
