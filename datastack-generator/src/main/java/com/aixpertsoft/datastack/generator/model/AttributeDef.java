package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** An attribute: a real column by default, or a JSON attribute when storage is "json". */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeDef(String name,
                           String type,
                           String storage,
                           Integer length,
                           Boolean required,
                           Boolean unique,
                           List<String> values,
                           List<AttributeDef> properties) {

    public boolean isJson() {
        return "json".equals(storage);
    }
}
