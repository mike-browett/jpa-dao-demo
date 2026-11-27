package com.aixpertsoft.datastack.generator;

import com.aixpertsoft.datastack.generator.model.AttributeDef;
import com.aixpertsoft.datastack.generator.model.Model;
import com.aixpertsoft.datastack.generator.model.ObjectDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;
import com.aixpertsoft.datastack.generator.model.ViewDef;
import java.nio.file.Files;
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
        if (blank(model.javaPackage())) problems.add("model: \"javaPackage\" is missing");

        unique("object", model.objects(), ObjectDef::id, problems);
        unique("relation", model.relations(), RelationDef::id, problems);
        unique("view", model.views(), ViewDef::id, problems);

        Map<String, ObjectDef> objects = model.objects().stream()
                .filter(o -> !blank(o.id()))
                .collect(Collectors.toMap(ObjectDef::id, Function.identity(), (a, b) -> a));

        model.objects().forEach(o -> checkObject(o, objects, problems));
        model.relations().forEach(r -> checkRelation(r, objects, problems));
        model.views().forEach(v -> checkView(v, modelDir, problems));
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
        if ("enum".equals(attribute.type()) && (attribute.values() == null || attribute.values().isEmpty())) {
            problems.add(at + ": enum needs \"values\"");
        }
        if ("object".equals(attribute.type()) && !attribute.isJson()) {
            problems.add(at + ": type \"object\" is only allowed with \"storage\": \"json\"");
        }
        if (attribute.properties() != null) {
            attribute.properties().forEach(p -> checkAttribute(at, p, problems));
        }
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
            relation.intersection().attributes().forEach(a -> checkAttribute(where + " (intersection)", a, problems));
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

    private void checkView(ViewDef view, Path modelDir, List<String> problems) {
        if (blank(view.query())) {
            problems.add("view " + view.id() + ": \"query\" is missing");
        } else if (!Files.isRegularFile(modelDir.resolve(view.query()))) {
            problems.add("view " + view.id() + ": query file " + view.query() + " not found");
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
