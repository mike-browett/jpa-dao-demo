package com.aixpertsoft.datastack.generator.gen;

import static com.aixpertsoft.datastack.generator.Names.lower;
import static com.aixpertsoft.datastack.generator.Names.singular;
import static com.aixpertsoft.datastack.generator.Names.upper;

import com.aixpertsoft.datastack.generator.ModelIndex;
import com.aixpertsoft.datastack.generator.ModelIndex.Entity;
import com.aixpertsoft.datastack.generator.ModelIndex.ToOne;
import com.aixpertsoft.datastack.generator.model.AttributeDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;
import java.util.ArrayList;
import java.util.List;

/**
 * API records (generated only): one DTO per hierarchy (e.g. ComponentDto, also used for racks), and
 * Create / Update requests per object with validation from the model.
 */
public final class DtoGenerator {

    private final ModelIndex index;
    private final Output out;
    private final String pkg;

    public DtoGenerator(ModelIndex index, Output out) {
        this.index = index;
        this.out = out;
        this.pkg = index.pkg("api");
    }

    public void generate() {
        for (Entity e : index.entities()) {
            if (e.isIntersection()) {
                linkDto(e);
            } else {
                if (index.isRoot(e)) dto(e);
                if (e.def().rest() != null) requests(e);
            }
        }
        for (RelationDef r : index.model().relations()) {
            if (r.isManyToMany() && r.rest() != null) linkRequests(r);
        }
    }

    /** Name of the DTO used for an entity: the DTO of its hierarchy root. */
    public static String dtoName(ModelIndex index, Entity e) {
        return index.root(e).name() + "Dto";
    }

    private void dto(Entity root) {
        Src s = new Src(pkg);
        s.imports("java.util.UUID");
        String entity = s.type(index.pkg("entity") + "." + root.name());
        List<String> components = new ArrayList<>(List.of("UUID id", "long version"));
        List<String> values = new ArrayList<>(List.of("e.getId()", "e.getVersion()"));
        if (index.hasDiscriminator(root)) {
            components.add("String type");
            values.add("e.getType()");
        }
        for (AttributeDef a : index.hierarchyAttributes(root)) {
            Entity owner = index.declaringEntity(root, a);
            components.add(s.type(index.javaType(owner, a)) + " " + a.name());
            String getter = ("boolean".equals(a.type()) ? "is" : "get") + upper(a.name()) + "()";
            if (owner == root) {
                values.add("e." + getter);
            } else {
                String sub = s.type(index.pkg("entity") + "." + owner.name());
                values.add("e instanceof " + sub + " x ? x." + getter + " : null");
            }
        }
        for (ToOne r : index.hierarchyToOnes(root)) {
            Entity owner = index.hierarchyBelow(root).stream().filter(x -> index.ownToOnes(x).contains(r)).findFirst().orElse(root);
            components.add("UUID " + r.name() + "Id");
            String ref = "e.get" + upper(r.name()) + "()";
            if (owner == root) {
                values.add(ref + " != null ? " + ref + ".getId() : null");
            } else {
                String sub = s.type(index.pkg("entity") + "." + owner.name());
                values.add("e instanceof " + sub + " x && x.get" + upper(r.name()) + "() != null ? x.get"
                        + upper(r.name()) + "().getId() : null");
            }
        }
        String name = root.name() + "Dto";
        String subtypes = index.subtypes(root).isEmpty() ? "" : " Also used for its subtypes; their fields are null for other types.";
        s.line("/** " + root.name() + " as returned by the API." + subtypes + " */");
        s.line("public record " + name + "(" + String.join(",\n        ", components) + ") {");
        s.line();
        s.line("    public static " + name + " of(" + entity + " e) {");
        s.line("        return new " + name + "(");
        s.line("                " + String.join(",\n                ", values) + ");");
        s.line("    }");
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    private void requests(Entity e) {
        Src s = new Src(pkg);
        s.imports("java.util.UUID");
        String name = e.name() + "Requests";
        List<String> create = new ArrayList<>();
        List<String> update = new ArrayList<>(List.of("Long version"));
        for (AttributeDef a : index.allAttributes(e)) {
            String t = s.type(boxed(index.javaType(index.declaringEntity(index.root(e), a), a)));
            create.add(validation(s, a, true) + t + " " + a.name());
            update.add(validation(s, a, false) + t + " " + a.name());
        }
        for (ToOne r : index.allToOnes(e)) {
            String notNull = r.required() ? "@" + s.type("jakarta.validation.constraints.NotNull") + " " : "";
            create.add(notNull + "UUID " + r.name() + "Id");
        }
        s.line("/** Request bodies for " + e.name() + ". */");
        s.line("public final class " + name + " {");
        s.line();
        s.line("    private " + name + "() {");
        s.line("    }");
        s.line();
        s.line("    public record Create(" + String.join(",\n                         ", create) + ") {");
        s.line("    }");
        s.line();
        s.line("    /** Partial update: null fields stay unchanged. Send \"version\" for optimistic locking. */");
        s.line("    public record Update(" + String.join(",\n                         ", update) + ") {");
        s.line("    }");
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    private void linkDto(Entity link) {
        RelationDef r = link.intersectionOf();
        Src s = new Src(pkg);
        s.imports("java.util.UUID");
        String entity = s.type(index.pkg("entity") + "." + link.name());
        Entity to = index.entity(r.to().object());
        String from = lower(r.from().object());
        String toName = lower(r.to().object());
        List<String> components = new ArrayList<>(List.of("UUID " + from + "Id"));
        List<String> values = new ArrayList<>(List.of("e.get" + upper(from) + "().getId()"));
        for (AttributeDef a : link.def().attributes()) {
            components.add(s.type(index.javaType(link, a)) + " " + a.name());
            values.add("e." + ("boolean".equals(a.type()) ? "is" : "get") + upper(a.name()) + "()");
        }
        components.add(dtoName(index, to) + " " + toName);
        values.add(dtoName(index, to) + ".of(e.get" + upper(toName) + "())");
        String name = link.name() + "Dto";
        s.line("/** One link of relation " + r.id() + ": the " + toName + " and the link's own attributes. */");
        s.line("public record " + name + "(" + String.join(", ", components) + ") {");
        s.line();
        s.line("    public static " + name + " of(" + entity + " e) {");
        s.line("        return new " + name + "(" + String.join(", ", values) + ");");
        s.line("    }");
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    private void linkRequests(RelationDef r) {
        Src s = new Src(pkg);
        s.imports("java.util.UUID", "jakarta.validation.constraints.NotNull");
        Entity link = index.entity(r.id());
        String toName = lower(r.to().object());
        List<String> linkComponents = new ArrayList<>(List.of("@NotNull UUID " + toName + "Id"));
        List<String> updateComponents = new ArrayList<>();
        for (AttributeDef a : link.def().attributes()) {
            String t = s.type(boxed(index.javaType(link, a)));
            linkComponents.add(t + " " + a.name());
            updateComponents.add(t + " " + a.name());
        }
        String name = r.id() + "Requests";
        s.line("/** Request bodies for relation " + r.id() + " (" + r.from().object() + "." + r.from().name() + "). */");
        s.line("public final class " + name + " {");
        s.line();
        s.line("    private " + name + "() {");
        s.line("    }");
        s.line();
        s.line("    /** Adds a " + singular(r.from().name()) + ". */");
        s.line("    public record Link(" + String.join(", ", linkComponents) + ") {");
        s.line("    }");
        s.line();
        s.line("    /** Changes the link's attributes; null fields stay unchanged. */");
        s.line("    public record Update(" + String.join(", ", updateComponents) + ") {");
        s.line("    }");
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    private static String validation(Src s, AttributeDef a, boolean create) {
        StringBuilder v = new StringBuilder();
        if (create && a.isRequired() && a.defaultValue() == null) {
            v.append("string".equals(a.type()) ? "@" + s.type("jakarta.validation.constraints.NotBlank") + " "
                    : "@" + s.type("jakarta.validation.constraints.NotNull") + " ");
        }
        if ("string".equals(a.type()) && a.length() != null) {
            v.append("@").append(s.type("jakarta.validation.constraints.Size")).append("(max = ").append(a.length()).append(") ");
        }
        if (a.min() != null) v.append("@").append(s.type("jakarta.validation.constraints.Min")).append("(").append(a.min()).append(") ");
        if (a.max() != null) v.append("@").append(s.type("jakarta.validation.constraints.Max")).append("(").append(a.max()).append(") ");
        return v.toString();
    }

    private static String boxed(String type) {
        return type;
    }
}
