package com.aixpertsoft.datastack.generator;

import com.aixpertsoft.datastack.generator.model.AttributeDef;
import com.aixpertsoft.datastack.generator.model.Model;
import com.aixpertsoft.datastack.generator.model.ObjectDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolved view of a model for the generators: entities (objects plus the intersection entities of
 * many-to-many relations), hierarchies, inherited attributes, references and collections.
 */
public final class ModelIndex {

    /** One JPA entity to generate. */
    public record Entity(String name, ObjectDef def, RelationDef intersectionOf) {
        public boolean isIntersection() {
            return intersectionOf != null;
        }
    }

    /** A to-one reference field, e.g. Component.rack, RackAdministrator.person. */
    public record ToOne(String name, Entity target, String column, RelationDef relation, boolean required) {
    }

    /** A collection field, e.g. Rack.children (mappedBy "rack"), Rack.administrators (mappedBy "rack"). */
    public record ToMany(String name, Entity element, String mappedBy, RelationDef relation) {
    }

    private final Model model;
    private final Map<String, Entity> entities = new LinkedHashMap<>();

    public ModelIndex(Model model) {
        this.model = model;
        model.objects().forEach(o -> entities.put(o.id(), new Entity(o.id(), o, null)));
        for (RelationDef r : model.relations()) {
            if (r.isManyToMany() && r.intersection() != null) {
                ObjectDef def = new ObjectDef(r.id(), r.intersection().table(), null, null, null,
                        r.intersection().attributes(), null);
                entities.put(r.id(), new Entity(r.id(), def, r));
            }
        }
    }

    public Model model() {
        return model;
    }

    /**
     * The model's namespace, so several models can run in one server: REST {@link #restBase()},
     * GraphQL field {@link #graphqlField()} and types prefixed with {@link #graphqlPrefix()}, database {@link #schema()}.
     */
    public String id() {
        return model.id();
    }

    /** REST base path of the model, e.g. /api/hogwarts. */
    public String restBase() {
        return model.restBasePath() + "/" + model.id();
    }

    /** Database schema of the model's tables (model id unless "database.schema" says otherwise). */
    public String schema() {
        return model.database().schema() != null ? model.database().schema() : model.id();
    }

    /** Query field holding the model's views: Query.hogwarts. */
    public String graphqlField() {
        return model.id();
    }

    /** Prefix of every GraphQL type of the model, so type names never clash between models: Hogwarts... */
    public String graphqlPrefix() {
        return Names.upper(model.id());
    }

    public String pkg() {
        return model.javaPackage();
    }

    public String pkg(String sub) {
        return model.javaPackage() + "." + sub;
    }

    public List<Entity> entities() {
        return List.copyOf(entities.values());
    }

    public Entity entity(String name) {
        Entity e = entities.get(name);
        if (e == null) throw new IllegalArgumentException("Unknown object " + name);
        return e;
    }

    public Optional<Entity> parent(Entity e) {
        return Optional.ofNullable(e.def().extendsId()).map(this::entity);
    }

    public Entity root(Entity e) {
        Entity r = e;
        while (r.def().extendsId() != null) r = entity(r.def().extendsId());
        return r;
    }

    public boolean isRoot(Entity e) {
        return e.def().extendsId() == null;
    }

    public List<Entity> subtypes(Entity e) {
        return entities.values().stream().filter(x -> e.name().equals(x.def().extendsId())).toList();
    }

    /** e and all its descendants. */
    public List<Entity> hierarchyBelow(Entity e) {
        List<Entity> all = new ArrayList<>();
        all.add(e);
        subtypes(e).forEach(s -> all.addAll(hierarchyBelow(s)));
        return all;
    }

    /** Root of a hierarchy with subtypes (or explicit inheritance): needs a discriminator. */
    public boolean hasDiscriminator(Entity e) {
        Entity root = root(e);
        return root.def().inheritance() != null || !subtypes(root).isEmpty();
    }

    public String discriminatorColumn(Entity e) {
        ObjectDef.Inheritance i = root(e).def().inheritance();
        return i != null && i.discriminator() != null ? Names.snake(i.discriminator()) : "type";
    }

    public String discriminatorValue(Entity e) {
        if (isRoot(e)) {
            ObjectDef.Inheritance i = e.def().inheritance();
            return i != null && i.value() != null ? i.value() : e.name().toUpperCase();
        }
        return e.def().discriminatorValue();
    }

    public String table(Entity e) {
        return root(e).def().table();
    }

    // ---- attributes ----

    /** Own column attributes (not JSON). */
    public List<AttributeDef> ownColumns(Entity e) {
        return e.def().attributes().stream().filter(a -> !a.isJson()).toList();
    }

    public List<AttributeDef> ownJson(Entity e) {
        return e.def().attributes().stream().filter(AttributeDef::isJson).toList();
    }

    /** All attributes, inherited first. */
    public List<AttributeDef> allAttributes(Entity e) {
        List<AttributeDef> all = new ArrayList<>();
        parent(e).ifPresent(p -> all.addAll(allAttributes(p)));
        all.addAll(e.def().attributes());
        return all;
    }

    /** Attributes of a whole hierarchy (root and every subtype): the columns of the shared table. */
    public List<AttributeDef> hierarchyAttributes(Entity root) {
        return hierarchyBelow(root).stream().flatMap(x -> x.def().attributes().stream()).toList();
    }

    /** JSON attributes of the whole hierarchy: the components of its Ext record. */
    public List<AttributeDef> hierarchyJson(Entity root) {
        return hierarchyAttributes(root).stream().filter(AttributeDef::isJson).toList();
    }

    public Optional<AttributeDef> attribute(Entity e, String name) {
        return allAttributes(e).stream().filter(a -> a.name().equals(name)).findFirst();
    }

    /** An attribute of e or of one of its subtypes (e.g. Professor.subject seen from Wizard). */
    public Optional<AttributeDef> attributeInHierarchy(Entity e, String name) {
        return attribute(e, name).or(() -> hierarchyBelow(e).stream()
                .flatMap(x -> x.def().attributes().stream())
                .filter(a -> a.name().equals(name))
                .findFirst());
    }

    /** The subtype that declares an attribute (for attributes that only exist on a subtype). */
    public Entity declaringEntity(Entity root, AttributeDef a) {
        return hierarchyBelow(root).stream().filter(x -> x.def().attributes().contains(a)).findFirst().orElse(root);
    }

    public String column(AttributeDef a) {
        return a.column() != null ? a.column() : Names.snake(a.name());
    }

    /** Spell.category -> SpellCategory; Person.personType -> PersonType (already starts with the object name). */
    public String enumType(Entity owner, AttributeDef a) {
        String root = root(owner).name();
        return a.name().toLowerCase().startsWith(root.toLowerCase()) ? Names.upper(a.name()) : root + Names.upper(a.name());
    }

    /** Name of the record generated for a JSON object attribute, e.g. Person.contact -> PersonContact. */
    public String jsonType(Entity owner, AttributeDef a) {
        return root(owner).name() + Names.upper(a.name());
    }

    /** Java type of an attribute as seen on entities, DTOs and view nodes. */
    public String javaType(Entity owner, AttributeDef a) {
        if ("enum".equals(a.type())) return pkg("entity") + "." + enumType(owner, a);
        if ("object".equals(a.type())) return pkg("entity") + "." + jsonType(owner, a);
        return Types.java(a.type());
    }

    // ---- references ----

    /** To-one references declared on e itself (not inherited). */
    public List<ToOne> ownToOnes(Entity e) {
        List<ToOne> refs = new ArrayList<>();
        if (e.isIntersection()) {
            RelationDef r = e.intersectionOf();
            refs.add(new ToOne(Names.lower(r.from().object()), entity(r.from().object()),
                    Names.snake(r.from().object()) + "_id", r, true));
            refs.add(new ToOne(Names.lower(r.to().object()), entity(r.to().object()),
                    Names.snake(r.to().object()) + "_id", r, true));
            return refs;
        }
        for (RelationDef r : model.relations()) {
            if (r.isOneToMany() && r.to().object().equals(e.name())) {
                refs.add(new ToOne(r.to().name(), entity(r.from().object()), oneToManyColumn(r), r,
                        r.to().min() != null && r.to().min() > 0));
            }
        }
        return refs;
    }

    public List<ToOne> allToOnes(Entity e) {
        List<ToOne> all = new ArrayList<>();
        parent(e).ifPresent(p -> all.addAll(allToOnes(p)));
        all.addAll(ownToOnes(e));
        return all;
    }

    /** To-one references of a whole hierarchy: FK columns of the shared table. */
    public List<ToOne> hierarchyToOnes(Entity root) {
        return hierarchyBelow(root).stream().flatMap(x -> ownToOnes(x).stream()).toList();
    }

    public List<ToMany> ownToManys(Entity e) {
        List<ToMany> list = new ArrayList<>();
        for (RelationDef r : model.relations()) {
            if (r.isOneToMany() && r.from().object().equals(e.name())) {
                list.add(new ToMany(r.from().name(), entity(r.to().object()), r.to().name(), r));
            }
            if (r.isManyToMany() && r.from().object().equals(e.name())) {
                list.add(new ToMany(r.from().name(), entity(r.id()), Names.lower(r.from().object()), r));
            }
            if (r.isManyToMany() && r.to().object().equals(e.name())) {
                list.add(new ToMany(r.to().name(), entity(r.id()), Names.lower(r.to().object()), r));
            }
        }
        return list;
    }

    public String oneToManyColumn(RelationDef r) {
        return r.column() != null ? r.column() : Names.snake(r.to().name()) + "_id";
    }

    /** Relations exposed as REST sub-resources below this entity (the "from" side). */
    public List<RelationDef> restRelationsFrom(Entity e) {
        return model.relations().stream()
                .filter(r -> r.rest() != null && r.from().object().equals(e.name()))
                .toList();
    }

    /** oneToMany relations whose "from" is e or a subtype, with onDelete restrict: deletion must be checked. */
    public List<RelationDef> restrictingRelations(Entity e) {
        List<String> names = hierarchyBelow(e).stream().map(Entity::name).toList();
        return model.relations().stream()
                .filter(r -> r.isOneToMany() && names.contains(r.from().object()) && "restrict".equals(r.onDeleteFrom()))
                .toList();
    }

    public List<Entity> restEntities() {
        return entities.values().stream().filter(e -> !e.isIntersection() && e.def().rest() != null).toList();
    }
}
