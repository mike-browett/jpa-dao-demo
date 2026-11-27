package com.aixpertsoft.datastack.generator.gen;

import static com.aixpertsoft.datastack.generator.Names.lower;
import static com.aixpertsoft.datastack.generator.Names.upper;

import com.aixpertsoft.datastack.generator.ModelIndex;
import com.aixpertsoft.datastack.generator.ModelIndex.Entity;
import com.aixpertsoft.datastack.generator.model.AttributeDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration: the generated constraint-to-error mapping, plus files created once and then owned:
 * the Spring Boot application class, application.yaml and the Liquibase master and custom changelogs.
 */
public final class ConfigGenerator {

    private final ModelIndex index;
    private final Output out;

    public ConfigGenerator(ModelIndex index, Output out) {
        this.index = index;
        this.out = out;
    }

    public static String applicationClass(ModelIndex index) {
        return upper(index.model().id()) + "Application";
    }

    public void generate() {
        constraints();
        application();
        applicationYaml();
        changelogs();
    }

    private void constraints() {
        String pkg = index.pkg("config");
        Src s = new Src(pkg);
        s.imports("com.aixpertsoft.datastack.web.ConstraintMapping", "com.aixpertsoft.datastack.web.ConstraintMappings",
                "org.springframework.http.HttpStatus", "org.springframework.stereotype.Component", "java.util.List");
        List<String> mappings = new ArrayList<>();
        for (Entity e : index.entities()) {
            if (!index.isRoot(e)) continue;
            for (AttributeDef a : index.hierarchyAttributes(e)) {
                if (a.isUnique() && !a.isJson()) {
                    mappings.add("new ConstraintMapping(\"" + ChangelogGenerator.uniqueColumn(e.def().table(), index.column(a))
                            + "\", HttpStatus.CONFLICT, \"duplicate" + upper(a.name()) + "\",\n                        \"Another "
                            + lower(e.name()) + " already has this " + a.name() + "\")");
                }
            }
        }
        ChangelogGenerator changelog = new ChangelogGenerator(index, out);
        for (Entity e : index.entities()) {
            if (!e.isIntersection()) continue;
            RelationDef r = e.intersectionOf();
            String table = r.intersection().table();
            mappings.add("new ConstraintMapping(\"uq_" + table + "\", HttpStatus.CONFLICT, \""
                    + ServiceGenerator.duplicateLinkCode(index.model().id(), r) + "\",\n                        \"This "
                    + lower(r.to().object()) + " is already linked to this " + lower(r.from().object()) + "\")");
            for (RelationDef.UniqueWhere u : r.intersection().uniqueWhere()) {
                String constraint = ChangelogGenerator.exclusionName(table, changelog.exclusionColumns(e, u));
                String code = index.model().id() + "." + (u.code() != null ? u.code() : lower(r.id()) + "Constraint");
                String message = u.message() != null ? u.message()
                        : "Relation " + r.id() + ": " + u.where() + " is allowed only once";
                mappings.add("new ConstraintMapping(\"" + constraint + "\", HttpStatus.CONFLICT, \"" + code
                        + "\",\n                        \"" + message.replace("\"", "\\\"") + "\")");
            }
        }
        String name = upper(index.model().id()) + "Constraints";
        s.line("/** Database constraints of this model and the error codes clients see when one is violated. */");
        s.line("@Component");
        s.line("public class " + name + " implements ConstraintMappings {");
        s.line();
        s.line("    @Override");
        s.line("    public List<ConstraintMapping> mappings() {");
        s.line("        return List.of(");
        s.line("                " + String.join(",\n                ", mappings) + ");");
        s.line("    }");
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
    }

    private void application() {
        Src s = new Src(index.pkg());
        s.imports("org.springframework.boot.SpringApplication", "org.springframework.boot.autoconfigure.SpringBootApplication");
        String name = applicationClass(index);
        s.line("/** The " + index.model().id() + " microservice. Scans the data stack runtime and this model's package. */");
        s.line("@SpringBootApplication(scanBasePackages = {\"com.aixpertsoft.datastack\", \"" + index.pkg() + "\"})");
        s.line("public class " + name + " {");
        s.line();
        s.line("    public static void main(String[] args) {");
        s.line("        SpringApplication.run(" + name + ".class, args);");
        s.line("    }");
        s.line("}");
        out.ownedJava(index.pkg(), name, s.render(""));
    }

    private void applicationYaml() {
        String id = index.model().id();
        String env = id.toUpperCase();
        out.ownedResource("application.yaml", """
                # Created once by datastack-generator. This file is yours: the generator never overwrites it.
                spring:
                  application:
                    name: %1$s-service

                  # Used when PostgreSQL is not started through Docker Compose.
                  datasource:
                    url: ${%2$s_DB_URL:jdbc:postgresql://localhost:5432/%1$s}
                    username: ${%2$s_DB_USER:%1$s}
                    password: ${%2$s_DB_PASSWORD:%1$s}

                  jpa:
                    open-in-view: false
                    hibernate:
                      ddl-auto: validate          # Liquibase owns the schema

                  liquibase:
                    change-log: classpath:db/changelog/db.changelog-master.yaml

                  graphql:
                    graphiql:
                      enabled: true

                  docker:
                    compose:
                      lifecycle-management: start-only
                """.formatted(id, env));
    }

    private void changelogs() {
        out.ownedResource("db/changelog/db.changelog-master.yaml", """
                # Created once by datastack-generator. This file is yours.
                # The generated changelog comes from model.json; hand-written changesets go in custom.sql.
                databaseChangeLog:
                  - include:
                      file: %s
                  - include:
                      file: db/changelog/custom.sql
                """.formatted(ChangelogGenerator.path(index)));
        out.ownedResource("db/changelog/custom.sql", """
                --liquibase formatted sql
                -- Created once by datastack-generator. This file is yours: add hand-written changesets here,
                -- e.g. extra indexes, data fixes, or changes to tables after the first deployment.
                """);
    }
}
