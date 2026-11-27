package com.aixpertsoft.datastack.boot;

import com.aixpertsoft.datastack.rules.RuleDispatcher;
import com.aixpertsoft.datastack.web.ConstraintMappings;
import com.aixpertsoft.datastack.web.ProblemHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Spring Boot integration of the runtime. Applications that scan com.aixpertsoft.datastack themselves keep their
 * beans (every bean here is conditional on a missing one); generated models and the datastack server rely on this.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration")
public class DatastackAutoConfiguration {

    /** Where generated models put their GraphQL schema: one file per model, outside Spring's default graphql/ folder. */
    public static final String MODEL_SCHEMAS = "classpath*:datastack/graphql/*.graphqls";

    @Bean
    @ConditionalOnMissingBean
    RuleDispatcher ruleDispatcher(ApplicationContext context) {
        return new RuleDispatcher(context);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication
    ProblemHandler problemHandler(ObjectProvider<ConstraintMappings> constraints) {
        return new ProblemHandler(constraints.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication
    ModelsController datastackModelsController(ObjectProvider<DatastackModel> models) {
        return new ModelsController(models.orderedStream().toList());
    }

    /**
     * GraphQL: every model contributes "extend type Query { <model>: <Model>Query! }". The base Query type lives
     * here, so any number of models can share one schema. Only active when at least one model is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GraphQlSourceBuilderCustomizer.class)
    static class GraphQlModels {

        @Bean
        GraphQlSourceBuilderCustomizer datastackModelSchemas() {
            Resource[] models = modelSchemas();
            return builder -> {
                if (models.length == 0) return;
                List<Resource> all = new ArrayList<>();
                all.add(new ClassPathResource("datastack/datastack.graphqls"));
                all.addAll(Arrays.asList(models));
                builder.schemaResources(all.toArray(Resource[]::new));
            };
        }

        @Bean
        RuntimeWiringConfigurer datastackModelsQuery(ObjectProvider<DatastackModel> models) {
            return wiring -> {
                if (modelSchemas().length == 0) return;
                wiring.type("Query", type -> type.dataFetcher("datastackModels",
                        env -> models.orderedStream().sorted(Comparator.comparing(DatastackModel::id)).toList()));
            };
        }

        private static Resource[] modelSchemas() {
            try {
                return new PathMatchingResourcePatternResolver().getResources(MODEL_SCHEMAS);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
