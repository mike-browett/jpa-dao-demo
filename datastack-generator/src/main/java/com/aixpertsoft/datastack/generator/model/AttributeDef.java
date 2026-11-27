package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * An attribute: a real column by default, or a JSON attribute when storage is "json"
 * (stored in the root table's jsonb column "ext").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeDef(String name,
                           String type,
                           String storage,
                           String column,
                           Integer length,
                           Boolean required,
                           Boolean unique,
                           Integer min,
                           Integer max,
                           @JsonProperty("default") Object defaultValue,
                           List<String> values,
                           List<AttributeDef> properties) {

    public AttributeDef {
        values = values != null ? List.copyOf(values) : List.of();
        properties = properties != null ? List.copyOf(properties) : List.of();
    }

    public boolean isJson() {
        return "json".equals(storage);
    }

    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }

    public boolean isUnique() {
        return Boolean.TRUE.equals(unique);
    }
}
