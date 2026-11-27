package com.aixpertsoft.datastack.generator;

import com.aixpertsoft.datastack.generator.model.AttributeDef;
import com.aixpertsoft.datastack.generator.model.Model;
import com.aixpertsoft.datastack.generator.model.ObjectDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;
import com.aixpertsoft.datastack.generator.model.ViewDef;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checks a model before anything is generated: ids are unique, every reference between elements
 * resolves, and each element is complete. Returns readable problems; an empty list means the model is valid.
 */
public class ModelValidator {

    private static final Set<String> ATTRIBUTE_TYPES =
            Set.of("string", "integer", "long", "decimal", "boolean", "date", "timestamp", "uuid", "enum", "object");

    public List<String> validate(Model model, Path modelDir) {
        List<String> problems = new ArrayList<>();
        if (blank(model.id())) problems.add("model: \"id\" is missing");
        else if (!model.id().matches("[a-z][a-z0-9]*")) {
            problems.add("model: \"id\" must be lower case letters and digits (it is the REST path, GraphQL field and "
                    + "database schema of the model): " + model.id());
        }
        if (blank(model.javaPackage())) problems.add("model: \"javaPackage\" is missing");

        unique("object", model.objects(), ObjectDef::id, problems);
        unique("relation", model.relations(), RelationDef::id, problems);
        unique("view", model.views(), ViewDef::id, problems);

        Map<String, ObjectDef> objects = model.objects().stream()
                .filter(o -> !blank(o.id()))
                .collect(Collectors.toMap(ObjectDef::id, Function.identity(), (a, b) -> a));

        model.objects().forEach(o -> checkObject(o, objects, problems));
        model.relations().forEach(r -> checkRelation(r, objects, problems));
        if (problems.stream().noneMatch(p -> p.contains("unknown object") || p.contains("extends"))) {
            ModelIndex index = new ModelIndex(model);
            model.views().forEach(v -> checkView(v, index, problems));
        }
        checkRestPaths(model, problems);
        return problems;
    }

    private void checkObject(ObjectDef object, Map<String, ObjectDef> objects, List<String> problems) {
        String where = "object " + object.id();
        if (object.extendsId() != null) {
            ObjectDef parent = objects.get(object.extendsId());
            if (parent == null) {
                problems.add(where + ": extends unknown object " + object.extendsId());
            } else if (blank(object.discriminatorValue())) {
                problems.add(where + ": extends " + parent.id() + " but has no \"discriminatorValue\"");
            }
            if (cycle(object, objects)) {
                problems.add(where + ": \"extends\" forms a cycle");
            }
        } else if (blank(object.table())) {
            problems.add(where + ": root object needs a \"table\"");
        }
        if (object.inheritance() != null && object.inheritance().strategy() != null
                && !"singleTable".equals(object.inheritance().strategy())) {
            problems.add(where + ": only \"singleTable\" inheritance is generated so far");
        }
        Set<String> names = new HashSet<>();
        for (ObjectDef o = object; o != null; o = o.extendsId() == null ? null : objects.get(o.extendsId())) {
            for (AttributeDef a : o.attributes()) {
                if (o == object && !names.add(a.name())) {
                    problems.add(where + ": attribute " + a.name() + " is defined twice");
                } else if (o != object && names.contains(a.name())) {
                    problems.add(where + ": attribute " + a.name() + " is already defined in " + o.id());
                }
            }
            if (cycle(o, objects)) break;
        }
        object.attributes().forEach(a -> checkAttribute(where, a, problems));
    }

    private void checkAttribute(String where, AttributeDef attribute, List<String> problems) {
        String at = where + ", attribute " + attribute.name();
        if (blank(attribute.name())) {
            problems.add(where + ": attribute without a \"name\"");
            return;
        }
        if (!ATTRIBUTE_TYPES.contains(attribute.type())) {
            problems.add(at + ": unknown type \"" + attribute.type() + "\"");
        }
        if ("enum".equals(attribute.type()) && attribute.values().isEmpty()) {
            problems.add(at + ": enum needs \"values\"");
        }
        if ("object".equals(attribute.type()) && !attribute.isJson()) {
            problems.add(at + ": type \"object\" is only allowed with \"storage\": \"json\"");
        }
        if ("object".equals(attribute.type()) && attribute.properties().isEmpty()) {
            problems.add(at + ": type \"object\" needs \"properties\"");
        }
        attribute.properties().forEach(p -> {
            if ("object".equals(p.type()) || "enum".equals(p.type())) {
                problems.add(at + ", property " + p.name() + ": nested objects and enums are not generated yet");
            } else {
                checkAttribute(at, p, problems);
            }
        });
    }

    private void checkRelation(RelationDef relation, Map<String, ObjectDef> objects, List<String> problems) {
        String where = "relation " + relation.id();
        if (!RelationDef.ONE_TO_MANY.equals(relation.type()) && !RelationDef.MANY_TO_MANY.equals(relation.type())) {
            problems.add(where + ": \"type\" must be oneToMany or manyToMany, not \"" + relation.type() + "\"");
        }
        checkEnd(where, "from", relation.from(), objects, problems);
        checkEnd(where, "to", relation.to(), objects, problems);
        if (RelationDef.MANY_TO_MANY.equals(relation.type()) && relation.intersection() == null) {
            problems.add(where + ": manyToMany needs an \"intersection\"");
        }
        if (RelationDef.ONE_TO_MANY.equals(relation.type()) && relation.intersection() != null) {
            problems.add(where + ": oneToMany cannot have an \"intersection\"");
        }
        if (relation.intersection() != null) {
            if (blank(relation.intersection().table())) {
                problems.add(where + ": intersection needs a \"table\"");
            }
            relation.intersection().attributes().forEach(a -> checkAttribute(where + " (intersection)", a, problems));
            for (RelationDef.UniqueWhere u : relation.intersection().uniqueWhere()) {
                if (u.where() == null || !u.where().matches("\\w+\\s*=\\s*(true|false)")) {
                    problems.add(where + ": uniqueWhere \"where\" must look like \"<booleanAttribute> = true\"");
                }
            }
        }
        if (relation.isManyToMany() && relation.from() != null && relation.to() != null
                && relation.from().object() != null && relation.from().object().equals(relation.to().object())) {
            problems.add(where + ": many-to-many from an object to itself is not supported yet");
        }
    }

    private void checkEnd(String where, String side, RelationDef.End end, Map<String, ObjectDef> objects,
                          List<String> problems) {
        if (end == null) {
            problems.add(where + ": \"" + side + "\" is missing");
        } else if (!objects.containsKey(end.object())) {
            problems.add(where + ": \"" + side + "\" refers to unknown object " + end.object());
        } else if (blank(end.name())) {
            problems.add(where + ": \"" + side + "\" needs a \"name\" (the navigation name on " + end.object() + ")");
        }
    }

    private static final Set<String> PARAMETER_TYPES = Set.of("string", "integer", "long", "boolean", "uuid");

    private void checkView(ViewDef view, ModelIndex index, List<String> problems) {
        String where = "view " + view.id();
        if (view.root() == null) {
            problems.add(where + ": \"root\" is missing");
            return;
        }
        ViewDef.Node root = view.root();
        if (blank(root.query())) {
            problems.add(where + ": root needs a JPQL \"query\"");
        }
        for (ViewDef.Parameter p : root.parameters()) {
            if (!PARAMETER_TYPES.contains(p.type())) {
                problems.add(where + ", parameter " + p.name() + ": type must be one of " + PARAMETER_TYPES);
            }
            if (root.query() != null && !root.query().contains(":" + p.name())) {
                problems.add(where + ", parameter " + p.name() + ": not used in the root query");
            }
        }
        checkNode(where + " (root)", root, index, problems);
    }

    private void checkNode(String where, ViewDef.Node node, ModelIndex index, List<String> problems) {
        ModelIndex.Entity entity;
        try {
            entity = index.entity(node.object());
        } catch (IllegalArgumentException e) {
            problems.add(where + ": unknown object " + node.object());
            return;
        }
        if (node.fields().isEmpty()) {
            problems.add(where + ": \"fields\" is empty");
        }
        for (String field : node.fields()) {
            boolean builtIn = field.equals("id") || field.equals("version")
                    || (field.equals("type") && index.hasDiscriminator(entity));
            if (!builtIn && index.attributeInHierarchy(entity, field).isEmpty()) {
                problems.add(where + ": " + node.object() + " has no attribute " + field);
            }
        }
        for (ViewDef.Node link : node.links()) {
            String at = where + ", link " + link.name();
            if (blank(link.name())) {
                problems.add(where + ": link without a \"name\"");
                continue;
            }
            if (link.isQueryLink()) {
                if (!link.query().contains(":parentIds")) {
                    problems.add(at + ": query must filter on :parentIds");
                }
                if (!node.fields().contains("id")) {
                    problems.add(at + ": the parent needs \"id\" in its fields, to match children to parents");
                }
            } else if (link.isPathLink()) {
                boolean toOne = index.allToOnes(entity).stream()
                        .anyMatch(r -> r.name().equals(link.path()) && r.target().name().equals(link.object()));
                if (!toOne) {
                    problems.add(at + ": " + node.object() + "." + link.path() + " is not a reference to " + link.object());
                }
            } else {
                problems.add(at + ": needs either \"query\" + \"parentKey\" (batched) or \"path\" (navigation)");
            }
            checkNode(at, link, index, problems);
        }
    }

    private void checkRestPaths(Model model, List<String> problems) {
        Set<String> seen = new HashSet<>();
        for (ObjectDef o : model.objects()) {
            if (o.rest() != null && o.rest().path() != null && !seen.add(o.rest().path())) {
                problems.add("object " + o.id() + ": REST path " + o.rest().path() + " is used twice");
            }
        }
    }

    private static boolean cycle(ObjectDef start, Map<String, ObjectDef> objects) {
        Set<String> seen = new HashSet<>();
        for (ObjectDef o = start; o != null && o.extendsId() != null; o = objects.get(o.extendsId())) {
            if (!seen.add(o.id())) return true;
        }
        return false;
    }

    private static <T> void unique(String kind, List<T> elements, Function<T, String> id, List<String> problems) {
        Set<String> seen = new HashSet<>();
        for (T element : elements) {
            String value = id.apply(element);
            if (blank(value)) {
                problems.add(kind + " without an \"id\"");
            } else if (!seen.add(value)) {
                problems.add(kind + " " + value + " is defined twice");
            }
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
