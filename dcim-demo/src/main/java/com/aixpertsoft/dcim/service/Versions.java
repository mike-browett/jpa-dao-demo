package com.aixpertsoft.dcim.service;

import com.aixpertsoft.datastack.web.ApiException;
import org.springframework.http.HttpStatus;

/** Optimistic locking for clients: if they send the version they read, it must still be current. */
final class Versions {

    private Versions() {
    }

    static void check(Long expected, long actual) {
        if (expected != null && expected != actual) {
            throw new ApiException(HttpStatus.CONFLICT, "concurrentModification",
                    "The object was changed by someone else (version " + actual + ", you sent " + expected + ")");
        }
    }
}
