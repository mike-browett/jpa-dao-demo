package com.aixpertsoft.dcim.entity;

/** How to reach a person: humans usually by email or phone, AI agents by endpoint. */
public record PersonContact(String email, String phone, String endpoint) {
}
