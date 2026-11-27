package com.aixpertsoft.datastack.rules;

/** What a rule receives: the event and the entity about to be written. */
public final class BeforeSave<T> {

    private final Event event;
    private final T entity;

    public BeforeSave(Event event, T entity) {
        this.event = event;
        this.entity = entity;
    }

    public Event event() {
        return event;
    }

    public T entity() {
        return entity;
    }

    /** Refuses the change. The transaction is rolled back and the caller gets HTTP 422 with this code. */
    public void reject(String code, String message) {
        throw new RuleViolation(code, message);
    }
}
