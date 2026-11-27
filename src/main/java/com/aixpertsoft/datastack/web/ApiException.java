package com.aixpertsoft.datastack.web;

import org.springframework.http.HttpStatus;

/** An error with an HTTP status and a stable code, e.g. 409 dcim.rackNotEmpty. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
