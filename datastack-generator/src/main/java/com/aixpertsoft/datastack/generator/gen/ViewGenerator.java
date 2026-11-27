package com.aixpertsoft.datastack.generator.gen;

import static com.aixpertsoft.datastack.generator.Names.lower;
import static com.aixpertsoft.datastack.generator.Names.upper;

import com.aixpertsoft.datastack.generator.ModelIndex;
import com.aixpertsoft.datastack.generator.ModelIndex.Entity;
import com.aixpertsoft.datastack.generator.Types;
import com.aixpertsoft.datastack.generator.model.AttributeDef;
import com.aixpertsoft.datastack.generator.model.ViewDef;
import com.aixpertsoft.datastack.generator.model.ViewDef.Node;
import com.aixpertsoft.datastack.generator.model.ViewDef.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Views (generated only). For each view in the model:
 * <ul>
 *   <li>records for every node of the graph (RackOverviewNode, RackOverviewChildrenNode, ...)</li>
 *   <li>a view service: the root JPQL query, one batched JPQL query per query link</li>
 *   <li>GraphQL types, a query field and @BatchMapping resolvers</li>
 *   <li>a stored GraphQL document and a REST endpoint /api/views/{path} that runs it</li>
 * </ul>
 */
public final class ViewGenerator {

    private final ModelIndex index;
    private final Output out;
    private final String pkg;
    private final StringBuilder schema = new StringBuilder();
    private final Map<String, String> enums = new LinkedHashMap<>();
    private final Map<String, AttributeDef> jsonTypes = new LinkedHashMap<>();
    private final List<String> queryFields = new ArrayList<>();

    public ViewGenerator(ModelIndex index, Output out) {
        this.index = index;
        this.out = out;
        this.pkg = index.pkg("view");
    }

    public void generate() {
        if (index.model().views().isEmpty()) return;
        for (ViewDef v : index.model().views()) {
            view(v);
        }
        schema();
        restController();
    }

    // ---- naming ----

    private static String typeName(ViewDef v, List<String> path) {
        StringBuilder n = new StringBuilder(v.id());
        path.forEach(p -> n.append(upper(p)));
        return n.toString();
    }

    private static String fieldName(ViewDef v) {
        return lower(v.id());
    }

    private void view(ViewDef v) {
        List<NodeInfo> nodes = new ArrayList<>();
        collect(v, v.root(), new ArrayList<>(), null, nodes);
        nodes.forEach(n -> nodeRecord(v, n));
        pageRecord(v);
        service(v, nodes);
        graphQlController(v, nodes);
        nodes.forEach(n -> schemaType(n));
        schema.append("type ").append(v.id()).append("Page {\n  page: Int!\n  size: Int!\n  total: Int\n  items: [")
              .append(v.id()).append("!]!\n}\n\n");
        List<String> args = new ArrayList<>(List.of("page: Int = 0", "size: Int = 20"));
        for (Parameter p : v.root().parameters()) {
            args.add(p.name() + ": " + Types.graphql(p.type()) + (p.defaultValue() != null ? " = " + graphqlLiteral(p.defaultValue()) : ""));
        }
        queryFields.add("  \"View " + v.id() + "\"\n  " + fieldName(v) + "(" + String.join(", ", args) + "): " + v.id() + "Page!");
        document(v);
    }

    /** One node of a view graph, with its place in the tree. */
    private record NodeInfo(Node node, Entity entity, String type, NodeInfo parent, List<String> path) {
        String record() {
            return type + "Node";
        }
    }

    private void collect(ViewDef v, Node node, List<String> path, NodeInfo parent, List<NodeInfo> into) {
        NodeInfo info = new NodeInfo(node, index.entity(node.object()), typeName(v, path), parent, List.copyOf(path));
        into.add(info);
        for (Node link : node.links()) {
            List<String> sub = new ArrayList<>(path);
            sub.add(link.name());
            collect(v, link, sub, info, into);
        }
    }

    private List<NodeInfo> children(NodeInfo n, List<NodeInfo> all) {
        return all.stream().filter(x -> x.parent() == n).toList();
    }

    // ---- fields ----

    private record Field(String name, String javaType, String graphqlType, String getter) {
    }

    private Field field(Entity e, String name, Src s) {
        switch (name) {
            case "id":
                return new Field("id", s.type("java.util.UUID"), "ID!", "getId()");
            case "version":
                return new Field("version", "Long", "String!", "getVersion()");
            case "type":
                if (index.attribute(e, "type").isEmpty()) return new Field("type", "String", "String!", "getType()");
                break;
            default:
                break;
        }
        AttributeDef a = index.attribute(e, name).orElseThrow();
        Entity owner = index.declaringEntity(index.root(e), a);
        String getter = ("boolean".equals(a.type()) ? "is" : "get") + upper(a.name()) + "()";
        String bang = a.isRequired() ? "!" : "";
        String gql;
        if ("enum".equals(a.type())) {
            gql = index.enumType(owner, a);
            enums.put(gql, String.join("\n  ", a.values()));
        } else if ("object".equals(a.type())) {
            gql = index.jsonType(owner, a);
            jsonTypes.put(gql, a);
        } else {
            gql = Types.graphql(a.type());
        }
        String javaType = "boolean".equals(a.type()) ? "boolean" : s.type(index.javaType(owner, a));
        return new Field(a.name(), javaType, gql + bang, getter);
    }

    // ---- records ----

    private void nodeRecord(ViewDef v, NodeInfo n) {
        Src s = new Src(pkg);
        List<String> components = new ArrayList<>();
        for (String f : n.node().fields()) {
            Field field = field(n.entity(), f, s);
            components.add(field.javaType() + " " + field.name());
        }
        for (Node link : n.node().links()) {
            if (link.isPathLink()) {
                components.add(typeName(v, append(n.path(), link.name())) + "Node " + link.name());
            }
        }
        s.line("/** GraphQL type " + n.type() + " of view " + v.id() + " (" + n.entity().name() + "). */");
        s.line("public record " + n.record() + "(" + String.join(", ", components) + ") {");
        s.line("}");
        out.generatedJava(pkg, n.record(), s.render(Output.GENERATED));
    }

    private void pageRecord(ViewDef v) {
        Src s = new Src(pkg);
        s.imports("java.util.List");
        s.line("/** One page of view " + v.id() + ". */");
        s.line("public record " + v.id() + "Page(int page, int size, Long total, List<" + v.id() + "Node> items) {");
        s.line("}");
        out.generatedJava(pkg, v.id() + "Page", s.render(Output.GENERATED));
    }

    // ---- data access ----

    private void service(ViewDef v, List<NodeInfo> nodes) {
        Src s = new Src(pkg);
        s.imports("jakarta.persistence.EntityManager", "jakarta.persistence.PersistenceContext",
                "org.springframework.stereotype.Service", "org.springframework.transaction.annotation.Transactional",
                "java.util.List");
        NodeInfo root = nodes.getFirst();
        String rootEntity = s.type(index.pkg("entity") + "." + root.entity().name());
        String name = v.id() + "ViewService";
        s.line("/** Data access of view " + v.id() + ": one JPQL query per level of the graph, whatever the page size. */");
        s.line("@Service");
        s.line("@Transactional(readOnly = true)");
        s.line("public class " + name + " {");
        s.line();
        s.line("    @PersistenceContext");
        s.line("    private EntityManager em;");
        s.line();
        List<String> params = new ArrayList<>(List.of("int page", "int size"));
        for (Parameter p : v.root().parameters()) {
            params.add(s.type(Types.java(p.type())) + " " + p.name());
        }
        s.line("    public " + v.id() + "Page root(" + String.join(", ", params) + ") {");
        s.line("        var query = em.createQuery(\"" + escape(v.root().query()) + "\", " + rootEntity + ".class);");
        for (Parameter p : v.root().parameters()) {
            s.line("        query.setParameter(\"" + p.name() + "\", " + p.name() + ");");
        }
        s.line("        List<" + root.record() + "> items = query.setFirstResult(page * size).setMaxResults(size)");
        s.line("                .getResultStream().map(" + name + "::to" + root.type() + ").toList();");
        if (v.root().count() != null) {
            s.line("        var count = em.createQuery(\"" + escape(v.root().count()) + "\", Long.class);");
            for (Parameter p : v.root().parameters()) {
                s.line("        count.setParameter(\"" + p.name() + "\", " + p.name() + ");");
            }
            s.line("        return new " + v.id() + "Page(page, size, count.getSingleResult(), items);");
        } else {
            s.line("        return new " + v.id() + "Page(page, size, null, items);");
        }
        s.line("    }");

        for (NodeInfo n : nodes) {
            if (n.node().isQueryLink()) {
                s.imports("java.util.Map", "java.util.UUID", "java.util.LinkedHashMap", "java.util.function.Function",
                        "java.util.stream.Collectors");
                String entity = s.type(index.pkg("entity") + "." + n.entity().name());
                String parentRecord = n.parent().record();
                String key = getterChain(n.node().parentKey());
                s.line();
                s.line("    /** Link " + String.join(".", n.path()) + " for all parents at once. */");
                s.line("    public Map<" + parentRecord + ", List<" + n.record() + ">> " + lower(n.type()) + "(List<" + parentRecord
                        + "> parents) {");
                s.line("        List<UUID> ids = parents.stream().map(" + parentRecord + "::id).toList();");
                s.line("        Map<UUID, List<" + n.record() + ">> byParent = em.createQuery(\"" + escape(n.node().query()) + "\", "
                        + entity + ".class)");
                s.line("                .setParameter(\"parentIds\", ids)");
                s.line("                .getResultStream()");
                s.line("                .collect(Collectors.groupingBy(e -> e." + key + ", LinkedHashMap::new,");
                s.line("                        Collectors.mapping(" + name + "::to" + n.type() + ", Collectors.toList())));");
                s.line("        return parents.stream().collect(Collectors.toMap(Function.identity(),");
                s.line("                p -> byParent.getOrDefault(p.id(), List.of()), (a, b) -> a, LinkedHashMap::new));");
                s.line("    }");
            }
        }
        for (NodeInfo n : nodes) {
            String entity = s.type(index.pkg("entity") + "." + n.entity().name());
            List<String> values = new ArrayList<>();
            for (String f : n.node().fields()) {
                values.add("e." + field(n.entity(), f, s).getter());
            }
            for (Node link : n.node().links()) {
                if (link.isPathLink()) {
                    String sub = typeName(v, append(n.path(), link.name()));
                    values.add("e.get" + upper(link.path()) + "() != null ? to" + sub + "(e.get" + upper(link.path())
                            + "()) : null");
                }
            }
            s.line();
            s.line("    static " + n.record() + " to" + n.type() + "(" + entity + " e) {");
            s.line("        return new " + n.record() + "(" + String.join(", ", values) + ");");
            s.line("    }");
        }
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    // ---- GraphQL ----

    private void graphQlController(ViewDef v, List<NodeInfo> nodes) {
        Src s = new Src(pkg);
        s.imports("org.springframework.stereotype.Controller", "org.springframework.graphql.data.method.annotation.Argument",
                "org.springframework.graphql.data.method.annotation.QueryMapping",
                "com.aixpertsoft.datastack.web.Paging");
        String service = v.id() + "ViewService";
        String name = v.id() + "GraphQlController";
        s.line("/** Resolvers of view " + v.id() + ": Spring batches each link into one call per level. */");
        s.line("@Controller");
        s.line("public class " + name + " {");
        s.line();
        s.line("    private final " + service + " view;");
        s.line();
        s.line("    public " + name + "(" + service + " view) {");
        s.line("        this.view = view;");
        s.line("    }");
        s.line();
        List<String> params = new ArrayList<>(List.of("@Argument Integer page", "@Argument Integer size"));
        List<String> args = new ArrayList<>(List.of("Paging.page(page)", "Paging.size(size)"));
        for (Parameter p : v.root().parameters()) {
            params.add("@Argument " + s.type(Types.java(p.type())) + " " + p.name());
            String fallback = p.defaultValue() != null ? Types.javaLiteral(p.type(), p.defaultValue()) : null;
            args.add(fallback != null ? p.name() + " != null ? " + p.name() + " : " + fallback : p.name());
        }
        s.line("    @QueryMapping");
        s.line("    public " + v.id() + "Page " + fieldName(v) + "(" + String.join(", ", params) + ") {");
        s.line("        return view.root(" + String.join(", ", args) + ");");
        s.line("    }");
        for (NodeInfo n : nodes) {
            if (n.node().isQueryLink()) {
                s.imports("org.springframework.graphql.data.method.annotation.BatchMapping", "java.util.List", "java.util.Map");
                s.line();
                s.line("    @BatchMapping(typeName = \"" + n.parent().type() + "\", field = \"" + n.node().name() + "\")");
                s.line("    public Map<" + n.parent().record() + ", List<" + n.record() + ">> " + lower(n.type()) + "(List<"
                        + n.parent().record() + "> parents) {");
                s.line("        return view." + lower(n.type()) + "(parents);");
                s.line("    }");
            }
        }
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    private void schemaType(NodeInfo n) {
        Src scratch = new Src(pkg);
        schema.append("\"").append(n.entity().name()).append(" in view ").append(n.type()).append("\"\n");
        schema.append("type ").append(n.type()).append(" {\n");
        for (String f : n.node().fields()) {
            Field field = field(n.entity(), f, scratch);
            schema.append("  ").append(field.name()).append(": ").append(field.graphqlType()).append('\n');
        }
        for (Node link : n.node().links()) {
            String sub = n.type() + upper(link.name());
            if (link.isQueryLink()) {
                schema.append("  ").append(link.name()).append(": [").append(sub).append("!]!\n");
            } else {
                schema.append("  ").append(link.name()).append(": ").append(sub).append('\n');
            }
        }
        schema.append("}\n\n");
    }

    private void schema() {
        StringBuilder full = new StringBuilder("""
                # GENERATED by datastack-generator from model.json. Do not edit.
                # Read model: one query field and one set of types per view. Writes go through the REST API.

                type Query {
                """);
        full.append(String.join("\n", queryFields)).append("\n}\n\n");
        full.append(schema);
        enums.forEach((name, values) -> full.append("enum ").append(name).append(" {\n  ").append(values).append("\n}\n\n"));
        jsonTypes.forEach((name, a) -> {
            full.append("type ").append(name).append(" {\n");
            a.properties().forEach(p -> full.append("  ").append(p.name()).append(": ").append(Types.graphql(p.type())).append('\n'));
            full.append("}\n\n");
        });
        out.generatedResource("graphql/" + index.model().id() + ".graphqls", full.toString().stripTrailing() + "\n");
    }

    private void document(ViewDef v) {
        List<String> vars = new ArrayList<>(List.of("$page: Int", "$size: Int"));
        List<String> args = new ArrayList<>(List.of("page: $page", "size: $size"));
        for (Parameter p : v.root().parameters()) {
            vars.add("$" + p.name() + ": " + Types.graphql(p.type()));
            args.add(p.name() + ": $" + p.name());
        }
        StringBuilder d = new StringBuilder("# GENERATED by datastack-generator: the full selection of view " + v.id() + ".\n");
        d.append("query ").append(v.id()).append("(").append(String.join(", ", vars)).append(") {\n");
        d.append("  ").append(fieldName(v)).append("(").append(String.join(", ", args)).append(") {\n");
        d.append("    page\n    size\n    total\n    items {\n");
        selection(v.root(), index.entity(v.root().object()), "      ", d);
        d.append("    }\n  }\n}\n");
        out.generatedResource("graphql-documents/" + v.id() + ".graphql", d.toString());
    }

    private void selection(Node node, Entity e, String indent, StringBuilder d) {
        Src scratch = new Src(pkg);
        for (String f : node.fields()) {
            d.append(indent).append(f);
            if (index.attribute(e, f).map(a -> "object".equals(a.type())).orElse(false)) {
                AttributeDef a = index.attribute(e, f).get();
                d.append(" { ").append(String.join(" ", a.properties().stream().map(AttributeDef::name).toList())).append(" }");
            }
            d.append('\n');
            field(e, f, scratch);
        }
        for (Node link : node.links()) {
            d.append(indent).append(link.name()).append(" {\n");
            selection(link, index.entity(link.object()), indent + "  ", d);
            d.append(indent).append("}\n");
        }
    }

    // ---- REST ----

    private void restController() {
        Src s = new Src(pkg);
        s.imports("org.springframework.web.bind.annotation.RestController", "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping", "org.springframework.web.bind.annotation.RequestParam",
                "org.springframework.graphql.ExecutionGraphQlService", "org.springframework.graphql.ExecutionGraphQlResponse",
                "org.springframework.graphql.support.DefaultExecutionGraphQlRequest", "org.springframework.core.io.ClassPathResource",
                "org.springframework.http.HttpStatus", "com.aixpertsoft.datastack.web.ApiException",
                "java.io.IOException", "java.io.UncheckedIOException", "java.nio.charset.StandardCharsets",
                "java.util.HashMap", "java.util.Map", "java.util.UUID");
        s.line("/** Every view as plain REST: runs the view's stored GraphQL document in-process and returns the page. */");
        s.line("@RestController");
        s.line("@RequestMapping(\"" + index.model().restBasePath() + "/views\")");
        s.line("public class ViewsController {");
        s.line();
        s.line("    private final ExecutionGraphQlService graphQl;");
        s.line();
        s.line("    public ViewsController(ExecutionGraphQlService graphQl) {");
        s.line("        this.graphQl = graphQl;");
        s.line("    }");
        for (ViewDef v : index.model().views()) {
            String path = v.rest() != null && v.rest().path() != null ? v.rest().path() : Names_kebab(v.id());
            List<String> params = new ArrayList<>(List.of("@RequestParam(required = false) Integer page",
                    "@RequestParam(required = false) Integer size"));
            for (Parameter p : v.root().parameters()) {
                params.add("@RequestParam(name = \"" + p.restName() + "\", required = false) " + s.type(Types.java(p.type())) + " "
                        + p.name());
            }
            s.line();
            s.line("    @GetMapping(\"/" + path + "\")");
            s.line("    public Object " + fieldName(v) + "(" + String.join(",\n            ", params) + ") {");
            s.line("        Map<String, Object> variables = new HashMap<>();");
            s.line("        variables.put(\"page\", page);");
            s.line("        variables.put(\"size\", size);");
            for (Parameter p : v.root().parameters()) {
                s.line("        variables.put(\"" + p.name() + "\", " + p.name() + ");");
            }
            s.line("        return run(\"graphql-documents/" + v.id() + ".graphql\", \"" + v.id() + "\", variables).get(\""
                    + fieldName(v) + "\");");
            s.line("    }");
        }
        s.line();
        s.line("    private Map<String, Object> run(String document, String operation, Map<String, Object> variables) {");
        s.line("        var request = new DefaultExecutionGraphQlRequest(load(document), operation, variables, null,");
        s.line("                UUID.randomUUID().toString(), null);");
        s.line("        ExecutionGraphQlResponse response = graphQl.execute(request).block();");
        s.line("        if (response == null || !response.isValid() || !response.getErrors().isEmpty()) {");
        s.line("            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, \"viewFailed\",");
        s.line("                    \"View \" + operation + \" failed: \" + (response == null ? \"no response\" : response.getErrors()));");
        s.line("        }");
        s.line("        return response.getData();");
        s.line("    }");
        s.line();
        s.line("    private static String load(String path) {");
        s.line("        try {");
        s.line("            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);");
        s.line("        } catch (IOException e) {");
        s.line("            throw new UncheckedIOException(e);");
        s.line("        }");
        s.line("    }");
        s.line("}");
        out.generatedJava(pkg, "ViewsController", s.render(Output.GENERATED));
    }

    // ---- helpers ----

    private static String Names_kebab(String id) {
        return id.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }

    private static List<String> append(List<String> path, String name) {
        List<String> p = new ArrayList<>(path);
        p.add(name);
        return p;
    }

    /** "rack.id" -> "getRack().getId()" */
    private static String getterChain(String path) {
        StringBuilder g = new StringBuilder();
        for (String part : path.split("\\.")) {
            if (!g.isEmpty()) g.append('.');
            g.append("get").append(upper(part)).append("()");
        }
        return g.toString();
    }

    private static String escape(String jpql) {
        return jpql.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String graphqlLiteral(Object value) {
        return value instanceof String str ? "\"" + str.replace("\"", "\\\"") + "\"" : String.valueOf(value);
    }
}
