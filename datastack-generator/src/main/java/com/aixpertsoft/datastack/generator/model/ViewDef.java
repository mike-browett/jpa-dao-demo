package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A view: a data graph read with JPQL. The root is one paged query; each link is either
 * <ul>
 *   <li>a <b>query</b> link: one JPQL query for all parents at once (":parentIds"), grouped by "parentKey", or</li>
 *   <li>a <b>path</b> link: navigates a to-one property of the parent entity (must be fetched by the parent's query).</li>
 * </ul>
 * The generator turns each view into GraphQL types, batched resolvers and a REST endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewDef(String id, Rest rest, Node root) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rest(String path) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Node(String name,
                       String object,
                       String query,
                       String count,
                       String parentKey,
                       String path,
                       List<Parameter> parameters,
                       List<String> fields,
                       List<Node> links) {

        public Node {
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            fields = fields != null ? List.copyOf(fields) : List.of();
            links = links != null ? List.copyOf(links) : List.of();
        }

        public boolean isQueryLink() {
            return query != null && parentKey != null;
        }

        public boolean isPathLink() {
            return path != null;
        }
    }

    /** A query parameter of the root query; "rest" is its name as a REST query parameter. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parameter(String name, String type, @JsonProperty("default") Object defaultValue, String rest) {
        public String restName() {
            return rest != null ? rest : name;
        }
    }
}
