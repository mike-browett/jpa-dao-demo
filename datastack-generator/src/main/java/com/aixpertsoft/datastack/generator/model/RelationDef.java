package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A relation between two objects: oneToMany, or manyToMany with an intersection object. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RelationDef(String id,
                          String type,
                          End from,
                          End to,
                          String column,
                          Intersection intersection) {

    public static final String ONE_TO_MANY = "oneToMany";
    public static final String MANY_TO_MANY = "manyToMany";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record End(String object, String name, Integer min) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Intersection(String table, List<AttributeDef> attributes, List<String> unique) {
        public Intersection {
            attributes = Model.orEmpty(attributes);
        }
    }
}
