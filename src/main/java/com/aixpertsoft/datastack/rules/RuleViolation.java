package com.aixpertsoft.datastack.rules;

/** A business rule refused a change. Carries a stable code for clients; mapped to HTTP 422. */
public class RuleViolation extends RuntimeException {

    private final String code;

    public RuleViolation(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
