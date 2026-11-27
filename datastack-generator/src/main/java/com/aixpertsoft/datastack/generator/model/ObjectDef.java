package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** An object (future JPA entity). Either a root with a table, or "extends" another object. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObjectDef(String id,
                        String table,
                        @JsonProperty("extends") String extendsId,
                        Inheritance inheritance,
                        String discriminatorValue,
                        List<AttributeDef> attributes,
                        Rest rest) {

    public ObjectDef {
        attributes = Model.orEmpty(attributes);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Inheritance(String strategy, String discriminator, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rest(String path, List<String> operations) {
    }
}
