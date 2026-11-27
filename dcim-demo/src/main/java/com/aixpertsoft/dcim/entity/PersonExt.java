package com.aixpertsoft.dcim.entity;

/**
 * Content of the person's jsonb "ext" column: the JSON attributes of Person.
 * Customer-specific JSON attributes will be added here later, without schema changes.
 */
public record PersonExt(PersonContact contact) {

    public static PersonExt empty() {
        return new PersonExt(null);
    }
}
