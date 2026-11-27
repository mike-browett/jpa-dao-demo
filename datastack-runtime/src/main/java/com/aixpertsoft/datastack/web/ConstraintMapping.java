package com.aixpertsoft.datastack.web;

import org.springframework.http.HttpStatus;

/**
 * Turns a database constraint violation into a readable error. {@code constraint} is matched against the
 * database error message, so use the constraint's name (e.g. "uq_component_serial_number").
 */
public record ConstraintMapping(String constraint, HttpStatus status, String code, String message) {
}
