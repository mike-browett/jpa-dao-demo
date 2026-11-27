package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A view: a stored GraphQL query (file path relative to the model) served as REST. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewDef(String id, String query, String provider) {
}
