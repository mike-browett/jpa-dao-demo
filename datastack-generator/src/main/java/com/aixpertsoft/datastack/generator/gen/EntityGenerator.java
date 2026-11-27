package com.aixpertsoft.datastack.generator.gen;

import static com.aixpertsoft.datastack.generator.Names.upper;

import com.aixpertsoft.datastack.generator.ModelIndex;
import com.aixpertsoft.datastack.generator.ModelIndex.Entity;
import com.aixpertsoft.datastack.generator.ModelIndex.ToMany;
import com.aixpertsoft.datastack.generator.ModelIndex.ToOne;
import com.aixpertsoft.datastack.generator.Types;
import com.aixpertsoft.datastack.generator.model.AttributeDef;
import java.util.List;

/**
 * JPA layer, using the generation gap:
 * <ul>
 *   <li>{@code RackBase} (generated): a @MappedSuperclass with every field, getter and setter from the model.</li>
 *   <li>{@code Rack} (owned, created once): the entity everyone uses; custom methods go here.</li>
 *   <li>{@code META-INF/datastack/<model>-orm.xml} (generated): which classes are entities, tables, inheritance, discriminators.
 *       Keeping that out of the owned classes means a model change never makes them stale.</li>
 * </ul>
 */
public final class EntityGenerator {

    private final ModelIndex index;
    private final Output out;
    private final String pkg;

    public EntityGenerator(ModelIndex index, Output out) {
        this.index = index;
        this.out = out;
        this.pkg = index.pkg("entity");
    }

    public void generate() {
        for (Entity e : index.entities()) {
            base(e);
            owned(e);
            for (AttributeDef a : e.def().attributes()) {
                if ("enum".equals(a.type())) enumType(e, a);
                if ("object".equals(a.type())) jsonRecord(e, a);
            }
            if (index.isRoot(e) && !index.hierarchyJson(e).isEmpty()) ext(e);
        }
        ormXml();
    }

    private void base(Entity e) {
        Src s = new Src(pkg);
        s.imports("jakarta.persistence.MappedSuperclass");
        String parent = index.parent(e).map(p -> " extends " + p.name()).orElse("");
        s.line("/** Fields, getters and setters of " + e.name() + " from the model. Custom code goes in " + e.name() + ". */");
        s.line("@MappedSuperclass");
        s.line("public abstract class " + e.name() + "Base" + parent + " {");
        s.line();
        boolean root = index.isRoot(e);
        if (root) {
            s.imports("jakarta.persistence.Id", "jakarta.persistence.GeneratedValue", "jakarta.persistence.GenerationType",
                    "jakarta.persistence.Version", "java.util.UUID");
            s.line("    @Id");
            s.line("    @GeneratedValue(strategy = GenerationType.UUID)");
            s.line("    protected UUID id;");
            s.line();
            s.line("    @Version");
            s.line("    protected long version;");
            s.line();
            if (index.hasDiscriminator(e)) {
                s.imports("jakarta.persistence.Column");
                s.line("    /** The discriminator, readable but written by Hibernate only. */");
                s.line("    @Column(name = \"" + index.discriminatorColumn(e) + "\", insertable = false, updatable = false)");
                s.line("    protected String type;");
                s.line();
            }
            if (!index.hierarchyJson(e).isEmpty()) {
                s.imports("jakarta.persistence.Column", "org.hibernate.annotations.JdbcTypeCode", "org.hibernate.type.SqlTypes");
                s.line("    /** JSON attributes, stored in the jsonb column \"ext\". */");
                s.line("    @JdbcTypeCode(SqlTypes.JSON)");
                s.line("    @Column(name = \"ext\", nullable = false, columnDefinition = \"jsonb\")");
                s.line("    protected " + e.name() + "Ext ext = " + e.name() + "Ext.empty();");
                s.line();
            }
        }
        for (AttributeDef a : index.ownColumns(e)) {
            s.imports("jakarta.persistence.Column");
            StringBuilder col = new StringBuilder("@Column(name = \"" + index.column(a) + "\"");
            if (a.isRequired() && root) col.append(", nullable = false");
            if ("string".equals(a.type()) && a.length() != null) col.append(", length = ").append(a.length());
            col.append(")");
            if ("enum".equals(a.type())) {
                s.imports("jakarta.persistence.Enumerated", "jakarta.persistence.EnumType");
                s.line("    @Enumerated(EnumType.STRING)");
            }
            s.line("    " + col);
            String init = a.defaultValue() != null ? Types.javaLiteral(a.type(), a.defaultValue()) : null;
            if (init == null && a.defaultValue() != null && "enum".equals(a.type())) {
                init = index.enumType(e, a) + "." + a.defaultValue();
            }
            s.line("    protected " + s.type(index.javaType(e, a)) + " " + a.name() + (init != null ? " = " + init : "") + ";");
            s.line();
        }
        for (ToOne r : index.ownToOnes(e)) {
            s.imports("jakarta.persistence.ManyToOne", "jakarta.persistence.FetchType", "jakarta.persistence.JoinColumn");
            s.line("    @ManyToOne(fetch = FetchType.LAZY" + (r.required() ? ", optional = false" : "") + ")");
            s.line("    @JoinColumn(name = \"" + r.column() + "\")");
            s.line("    protected " + r.target().name() + " " + r.name() + ";");
            s.line();
        }
        for (ToMany m : index.ownToManys(e)) {
            s.imports("jakarta.persistence.OneToMany", "java.util.List", "java.util.ArrayList");
            s.line("    @OneToMany(mappedBy = \"" + m.mappedBy() + "\")");
            s.line("    protected List<" + m.element().name() + "> " + m.name() + " = new ArrayList<>();");
            s.line();
        }

        // ---- accessors ----
        if (root) {
            s.line("    public UUID getId() { return id; }");
            s.line("    public long getVersion() { return version; }");
            if (index.hasDiscriminator(e)) {
                s.line();
                s.line("    /** " + index.discriminatorValue(e) + " for this class; before the first save the column is still empty. */");
                s.line("    public String getType() { return type != null ? type : discriminatorValue(); }");
                s.line();
                s.line("    protected String discriminatorValue() { return \"" + index.discriminatorValue(e) + "\"; }");
            }
            s.line();
        } else if (index.hasDiscriminator(e)) {
            s.line("    @Override");
            s.line("    protected String discriminatorValue() { return \"" + index.discriminatorValue(e) + "\"; }");
            s.line();
        }
        for (AttributeDef a : index.ownColumns(e)) {
            String t = s.type(index.javaType(e, a));
            if ("boolean".equals(a.type())) {
                s.line("    public boolean is" + upper(a.name()) + "() { return Boolean.TRUE.equals(" + a.name() + "); }");
            } else {
                s.line("    public " + t + " get" + upper(a.name()) + "() { return " + a.name() + "; }");
            }
            s.line("    public void set" + upper(a.name()) + "(" + t + " " + a.name() + ") { this." + a.name() + " = " + a.name() + "; }");
            s.line();
        }
        for (AttributeDef a : index.ownJson(e)) {
            String t = s.type(index.javaType(e, a));
            s.line("    public " + t + " get" + upper(a.name()) + "() { return ext." + a.name() + "(); }");
            s.line("    public void set" + upper(a.name()) + "(" + t + " " + a.name() + ") { this.ext = ext.with"
                    + upper(a.name()) + "(" + a.name() + "); }");
            s.line();
        }
        for (ToOne r : index.ownToOnes(e)) {
            s.line("    public " + r.target().name() + " get" + upper(r.name()) + "() { return " + r.name() + "; }");
            s.line("    public void set" + upper(r.name()) + "(" + r.target().name() + " " + r.name() + ") { this."
                    + r.name() + " = " + r.name() + "; }");
            s.line();
        }
        for (ToMany m : index.ownToManys(e)) {
            s.line("    public List<" + m.element().name() + "> get" + upper(m.name()) + "() { return " + m.name() + "; }");
            s.line();
        }
        s.line("}");
        out.generatedJava(pkg, e.name() + "Base", s.render(Output.GENERATED));
    }

    private void owned(Entity e) {
        Src s = new Src(pkg);
        s.line("/** The " + e.name() + " entity. Table and mapping come from the model (orm.xml, " + e.name() + "Base). */");
        s.line("public class " + e.name() + " extends " + e.name() + "Base {");
        s.line();
        s.line("    // custom methods, e.g. derived values used by rules and services");
        s.line("}");
        out.ownedJava(pkg, e.name(), s.render(""));
    }

    private void enumType(Entity e, AttributeDef a) {
        Src s = new Src(pkg);
        s.line("public enum " + index.enumType(e, a) + " {");
        s.line("    " + String.join(",\n    ", a.values()));
        s.line("}");
        out.generatedJava(pkg, index.enumType(e, a), s.render(Output.GENERATED));
    }

    private void jsonRecord(Entity e, AttributeDef a) {
        Src s = new Src(pkg);
        String name = index.jsonType(e, a);
        List<String> components = a.properties().stream()
                .map(p -> s.type(Types.java(p.type())) + " " + p.name()).toList();
        s.line("/** JSON attribute " + e.name() + "." + a.name() + ". */");
        s.line("public record " + name + "(" + String.join(", ", components) + ") {");
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    /** Content of the jsonb column "ext": all JSON attributes of a hierarchy. */
    private void ext(Entity root) {
        Src s = new Src(pkg);
        List<AttributeDef> json = index.hierarchyJson(root);
        List<String> components = json.stream()
                .map(a -> s.type(index.javaType(index.declaringEntity(root, a), a)) + " " + a.name()).toList();
        String name = root.name() + "Ext";
        s.line("/** Content of " + root.name() + "'s jsonb column \"ext\": its JSON attributes. */");
        s.line("public record " + name + "(" + String.join(", ", components) + ") {");
        s.line();
        s.line("    public static " + name + " empty() {");
        s.line("        return new " + name + "(" + String.join(", ", json.stream().map(a -> "null").toList()) + ");");
        s.line("    }");
        for (AttributeDef a : json) {
            String args = String.join(", ", json.stream().map(x -> x == a ? "value" : x.name()).toList());
            s.line();
            s.line("    public " + name + " with" + upper(a.name()) + "(" + s.type(index.javaType(index.declaringEntity(root, a), a))
                    + " value) {");
            s.line("        return new " + name + "(" + args + ");");
            s.line("    }");
        }
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    /** One mapping file per model (not META-INF/orm.xml, of which JPA only reads one). */
    public static String ormPath(ModelIndex index) {
        return "META-INF/datastack/" + index.id() + "-orm.xml";
    }

    private void ormXml() {
        StringBuilder x = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- GENERATED by datastack-generator from model.json. Do not edit. -->
                <!-- Which classes are entities and how they map to tables; fields are mapped in the *Base classes. -->
                <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence/orm https://jakarta.ee/xml/ns/persistence/orm/orm_3_1.xsd"
                                 version="3.1">
                    <!-- Every table is in schema %s: one schema per model. -->
                """.formatted(index.schema()));
        for (Entity e : index.entities()) {
            x.append("    <entity class=\"").append(pkg).append('.').append(e.name()).append("\">\n");
            if (index.isRoot(e)) {
                x.append("        <table name=\"").append(e.def().table()).append("\" schema=\"").append(index.schema()).append("\"/>\n");
            }
            if (index.hasDiscriminator(e)) {
                if (index.isRoot(e)) x.append("        <inheritance strategy=\"SINGLE_TABLE\"/>\n");
                x.append("        <discriminator-value>").append(index.discriminatorValue(e)).append("</discriminator-value>\n");
                if (index.isRoot(e)) {
                    x.append("        <discriminator-column name=\"").append(index.discriminatorColumn(e))
                     .append("\" length=\"30\"/>\n");
                }
            }
            x.append("    </entity>\n");
        }
        x.append("</entity-mappings>\n");
        out.generatedResource(ormPath(index), x.toString());
    }
}
