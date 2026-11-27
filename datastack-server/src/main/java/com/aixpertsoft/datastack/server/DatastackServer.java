package com.aixpertsoft.datastack.server;

import com.aixpertsoft.datastack.boot.DatastackModel;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Hosts every model whose jar is on the classpath (deployments/). Each model plugs itself in through its generated
 * auto-configuration; the server only provides the shared infrastructure: web, JPA, GraphQL, Liquibase, database.
 */
@SpringBootApplication
public class DatastackServer {

    private static final Logger log = LoggerFactory.getLogger(DatastackServer.class);

    public static void main(String[] args) {
        SpringApplication.run(DatastackServer.class, args);
    }

    @Bean
    ApplicationRunner listModels(List<DatastackModel> models) {
        return args -> {
            if (models.isEmpty()) {
                log.warn("No models deployed. Run ./gradlew :<model>-service:deploy, then restart.");
                return;
            }
            models.stream().sorted(Comparator.comparing(DatastackModel::id)).forEach(m ->
                    log.info("Model {} {}: REST {}, GraphQL Query.{}, schema {}", m.id(), m.version(), m.rest(),
                            m.graphql(), m.schema()));
        };
    }
}
