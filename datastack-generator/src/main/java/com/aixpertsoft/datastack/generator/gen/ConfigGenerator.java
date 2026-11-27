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

    public static String modelConfigurationClass(ModelIndex index) {
        return upper(index.id()) + "ModelConfiguration";
    }

    public static String changelogMaster(ModelIndex index) {
        return "db/changelog/" + index.id() + "/master.yaml";
    }

    public void generate() {
        constraints();
        modelConfiguration();
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
        s.imports("org.springframework.boot.SpringApplication", "org.springframework.boot.SpringBootConfiguration",
                "org.springframework.boot.autoconfigure.EnableAutoConfiguration");
        String name = applicationClass(index);
        s.line("/**");
        s.line(" * Runs the " + index.id() + " model on its own. It deliberately does no component scan: the model's beans come");
        s.line(" * from the generated " + modelConfigurationClass(index) + " (an auto-configuration), exactly as when the jar");
        s.line(" * is deployed into datastack-server next to other models.");
        s.line(" */");
        s.line("@SpringBootConfiguration");
        s.line("@EnableAutoConfiguration");
        s.line("public class " + name + " {");
        s.line();
        s.line("    public static void main(String[] args) {");
        s.line("        SpringApplication.run(" + name + ".class, args);");
        s.line("    }");
        s.line("}");
        out.ownedJava(index.pkg(), name, s.render(""));
    }

    /**
     * The model as a plug-in: an auto-configuration found through META-INF/spring/...AutoConfiguration.imports.
     * Any Spring Boot application with the model's jar on its classpath gets the model: its beans (with fully
     * qualified names, so two models may both have a PersonService), its Liquibase changelog in its own schema,
     * its JPA mapping file and its entry in the list of deployed models.
     */
    private void modelConfiguration() {
        String pkg = index.pkg("config");
        String id = index.id();
        String schema = index.schema();
        String name = modelConfigurationClass(index);
        Src s = new Src(pkg);
        s.imports("com.aixpertsoft.datastack.boot.DatastackAutoConfiguration", "com.aixpertsoft.datastack.boot.DatastackModel",
                "javax.sql.DataSource", "liquibase.integration.spring.SpringLiquibase",
                "org.springframework.boot.SpringBootConfiguration", "org.springframework.boot.autoconfigure.AutoConfiguration",
                "org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter",
                "org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryBuilderCustomizer",
                "org.springframework.context.annotation.Bean", "org.springframework.context.annotation.ComponentScan",
                "org.springframework.context.annotation.FilterType",
                "org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator",
                "org.springframework.core.env.Environment", "org.springframework.jdbc.core.JdbcTemplate");
        s.line("/**");
        s.line(" * The " + id + " model as a plug-in. Listed in META-INF/spring/...AutoConfiguration.imports, so any Spring Boot");
        s.line(" * application with this jar on its classpath hosts the model: REST under " + index.restBase() + ", GraphQL under");
        s.line(" * Query." + index.graphqlField() + ", tables in schema " + schema + ".");
        s.line(" */");
        s.line("@AutoConfiguration(after = DatastackAutoConfiguration.class, beforeName = {");
        s.line("        \"org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration\",");
        s.line("        \"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration\"})");
        s.line("@ComponentScan(basePackages = \"" + index.pkg() + "\",");
        s.line("        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,");
        s.line("        excludeFilters = {");
        s.line("                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class),");
        s.line("                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)})");
        s.line("public class " + name + " {");
        s.line();
        s.line("    public static final String ID = \"" + id + "\";");
        s.line("    public static final String SCHEMA = \"" + schema + "\";");
        s.line();
        s.line("    @Bean");
        s.line("    DatastackModel " + id + "Model() {");
        s.line("        return new DatastackModel(ID, " + (index.model().version() == null ? "null" : "\"" + index.model().version() + "\"")
                + ", \"" + index.restBase() + "\", \"" + index.graphqlField() + "\", SCHEMA);");
        s.line("    }");
        s.line();
        s.line("    /** The model's own Liquibase run, in its own schema. Set datastack.liquibase.drop-first=true to start empty. */");
        s.line("    @Bean");
        s.line("    SpringLiquibase " + id + "Liquibase(DataSource dataSource, Environment environment) {");
        s.line("        new JdbcTemplate(dataSource).execute(\"CREATE SCHEMA IF NOT EXISTS \" + SCHEMA);");
        s.line("        SpringLiquibase liquibase = new SpringLiquibase();");
        s.line("        liquibase.setDataSource(dataSource);");
        s.line("        liquibase.setChangeLog(\"classpath:" + changelogMaster(index) + "\");");
        s.line("        liquibase.setDefaultSchema(SCHEMA);");
        s.line("        liquibase.setLiquibaseSchema(SCHEMA);");
        s.line("        liquibase.setDropFirst(environment.getProperty(\"datastack.liquibase.drop-first\", Boolean.class, false));");
        s.line("        return liquibase;");
        s.line("    }");
        s.line();
        s.line("    /** Adds this model's mapping file to the application's single persistence unit. */");
        s.line("    @Bean");
        s.line("    EntityManagerFactoryBuilderCustomizer " + id + "Mapping() {");
        s.line("        return builder -> builder.addPersistenceUnitPostProcessors(");
        s.line("                unit -> unit.addMappingFileName(\"" + EntityGenerator.ormPath(index) + "\"));");
        s.line("    }");
        s.line("}");
        out.generatedJava(pkg, name, s.render(Output.GENERATED));
        out.generatedResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                pkg + "." + name + "\n");
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

                  # Liquibase runs per model, in the model's own schema (see the generated config/*ModelConfiguration).

                  graphql:
                    graphiql:
                      enabled: true

                  docker:
                    compose:
                      lifecycle-management: start-only
                """.formatted(id, env));
    }

    private void changelogs() {
        String dir = "db/changelog/" + index.id() + "/";
        out.ownedResource(changelogMaster(index), """
                # Created once by datastack-generator. This file is yours.
                # The generated changelog comes from model.json; hand-written changesets go in custom.sql.
                # Liquibase runs it with default schema %s.
                databaseChangeLog:
                  - include:
                      file: %s
                  - include:
                      file: %scustom.sql
                """.formatted(index.schema(), ChangelogGenerator.path(index), dir));
        out.ownedResource(dir + "custom.sql", """
                --liquibase formatted sql
                -- Created once by datastack-generator. This file is yours: add hand-written changesets here,
                -- e.g. extra indexes, data fixes, or changes to tables after the first deployment.
                -- Qualify tables with the model's schema: %s.<table>.
                """.formatted(index.schema()));
    }
}
