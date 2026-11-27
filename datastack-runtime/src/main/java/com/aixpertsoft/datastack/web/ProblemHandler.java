package com.aixpertsoft.datastack.web;

import com.aixpertsoft.datastack.dao.NotFoundException;
import com.aixpertsoft.datastack.rules.RuleViolation;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns exceptions into application/problem+json responses that always carry a stable "code". */
@RestControllerAdvice
public class ProblemHandler {

    private final List<ConstraintMappings> constraints;

    public ProblemHandler(List<ConstraintMappings> constraints) {
        this.constraints = constraints;
    }

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

    /** Malformed JSON or a value of the wrong type, e.g. an unknown enum constant. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        String detail = cause.getMessage() == null ? "" : cause.getMessage().lines().findFirst().orElse("");
        return problem(HttpStatus.BAD_REQUEST, "unreadable",
                detail.isBlank() ? "The request body could not be read" : detail);
    }

    /**
     * Database constraints are the last line of defence. Each microservice declares its constraints
     * through {@link ConstraintMappings}; anything else becomes a generic 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        return constraints.stream()
                .flatMap(c -> c.mappings().stream())
                .filter(m -> detail.contains(m.constraint()))
                .findFirst()
                .map(m -> problem(m.status(), m.code(), m.message()))
                .orElseGet(() -> problem(HttpStatus.CONFLICT, "constraintViolation",
                        "The change violates a database constraint"));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
