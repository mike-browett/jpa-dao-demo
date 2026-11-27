package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** An object (a JPA entity). Either a root with a table, or "extends" another object. */
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

    /** Only "singleTable" is generated in this prototype. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Inheritance(String strategy, String discriminator, String value) {
    }

    /** REST exposure: base path below restBasePath, and which operations. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rest(String path, List<String> operations) {
        public Rest {
            operations = operations != null ? List.copyOf(operations)
                    : List.of("list", "read", "create", "update", "delete");
        }

        public boolean allows(String operation) {
            return operations.contains(operation);
        }
    }
}
