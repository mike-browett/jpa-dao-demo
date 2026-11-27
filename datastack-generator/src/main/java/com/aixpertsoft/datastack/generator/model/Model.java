package com.aixpertsoft.datastack.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/** A microservice project's model, as read from model.json (includes merged in). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Model(String id,
                    String version,
                    String javaPackage,
                    String restBasePath,
                    List<String> dependsOn,
                    List<String> include,
                    List<ObjectDef> objects,
                    List<RelationDef> relations,
                    List<ViewDef> views) {

    public Model {
        dependsOn = orEmpty(dependsOn);
        include = orEmpty(include);
        objects = orEmpty(objects);
        relations = orEmpty(relations);
        views = orEmpty(views);
    }

    /** This model plus the elements of an included file. */
    public Model merge(Model other) {
        return new Model(id, version, javaPackage, restBasePath, dependsOn, include,
                concat(objects, other.objects), concat(relations, other.relations), concat(views, other.views));
    }

    static <T> List<T> orEmpty(List<T> list) {
        return list != null ? List.copyOf(list) : List.of();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> all = new ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }
}
