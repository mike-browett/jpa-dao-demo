package com.aixpertsoft.datastack.web;

/** Keeps page numbers and sizes within sensible bounds. */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static int page(Integer requested) {
        return requested == null ? 0 : Math.max(0, requested);
    }

    public static int size(Integer requested) {
        return requested == null ? DEFAULT_SIZE : Math.max(1, Math.min(requested, MAX_SIZE));
    }
}
