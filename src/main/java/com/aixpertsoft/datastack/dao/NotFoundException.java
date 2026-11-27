package com.aixpertsoft.datastack.dao;

/** Thrown when an object is looked up by id and does not exist; mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String type, Object id) {
        super(type + " " + id + " not found");
    }
}
