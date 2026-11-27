package com.aixpertsoft.dcim.rest;

final class Paging {

    static final int MAX_SIZE = 100;

    private Paging() {
    }

    static int size(int requested) {
        return Math.max(1, Math.min(requested, MAX_SIZE));
    }
}
