package com.aixpertsoft.datastack.rules;

/** Lifecycle events raised by the DAOs. All run inside the transaction, before the SQL is sent. */
public enum Event {
    BEFORE_INSERT,
    BEFORE_UPDATE,
    BEFORE_DELETE
}
