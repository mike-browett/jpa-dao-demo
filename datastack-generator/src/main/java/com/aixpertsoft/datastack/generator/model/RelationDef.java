package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * A relation between two objects.
 * <ul>
 *   <li>oneToMany: "from" is the one side (Rack.children), "to" the many side (Component.rack); FK in "to"'s table.</li>
 *   <li>manyToMany: an intersection entity named after the relation id, with optional attributes.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RelationDef(String id,
                          String type,
                          End from,
                          End to,
                          String column,
                          Intersection intersection,
                          Map<String, String> onDelete,
                          Rest rest) {

    public static final String ONE_TO_MANY = "oneToMany";
    public static final String MANY_TO_MANY = "manyToMany";

    public RelationDef {
        onDelete = onDelete != null ? Map.copyOf(onDelete) : Map.of();
    }

    public boolean isOneToMany() {
        return ONE_TO_MANY.equals(type);
    }

    public boolean isManyToMany() {
        return MANY_TO_MANY.equals(type);
    }

    /** What happens to the relation when the "from" object is deleted: restrict (default), cascadeLinks, setNull. */
    public String onDeleteFrom() {
        return onDelete.getOrDefault("from", isManyToMany() ? "cascadeLinks" : "restrict");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record End(String object, String name, Integer min) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Intersection(String table, List<AttributeDef> attributes, List<String> unique,
                               List<UniqueWhere> uniqueWhere) {
        public Intersection {
            attributes = attributes != null ? List.copyOf(attributes) : List.of();
            unique = unique != null ? List.copyOf(unique) : List.of("from", "to");
            uniqueWhere = uniqueWhere != null ? List.copyOf(uniqueWhere) : List.of();
        }
    }

    /** "columns": from / to / attribute names; "where": "<booleanAttribute> = true". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UniqueWhere(List<String> columns, String where) {
    }

    /** REST sub-resource below the "from" object, e.g. /racks/{id}/children. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rest(String path) {
    }
}
