package com.aixpertsoft.dcim.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * End-to-end test through the REST API against a real PostgreSQL: all layers, rules and constraints.
 *
 * <p>Uses a throw-away PostgreSQL container (Testcontainers) when Docker is available. Without Docker,
 * set PHASE1_TEST_DB_URL (plus PHASE1_TEST_DB_USER / PHASE1_TEST_DB_PASSWORD) to an empty test
 * database; it is wiped at the start. With neither, the test is skipped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"demo.seed=false", "spring.docker.compose.enabled=false"})
@EnabledIf("databaseAvailable")
class RackApiIntegrationTest {

    private static final String TEST_DB_URL = System.getenv("PHASE1_TEST_DB_URL");

    static boolean databaseAvailable() {
        return TEST_DB_URL != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (TEST_DB_URL != null) {
            registry.add("spring.datasource.url", () -> TEST_DB_URL);
            registry.add("spring.datasource.username", () -> env("PHASE1_TEST_DB_USER", "phase1"));
            registry.add("spring.datasource.password", () -> env("PHASE1_TEST_DB_PASSWORD", "phase1"));
            registry.add("spring.liquibase.drop-first", () -> "true");
        } else {
            PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @Value("${local.server.port}")
    int port;

    RestClient api;

    @BeforeEach
    void client() {
        api = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api")
                .defaultStatusHandler(status -> true, (request, response) -> { })   // assert statuses ourselves
                .build();
    }

    @Test
    void rackOverviewShowsChildrenAndAdministrators() {
        String rack = id(post("/racks", Map.of("name", "VIEW-R01", "heightUnits", 42)));
        post("/components", Map.of("name", "it-srv-01", "rackId", rack));
        String claire = id(post("/persons", Map.of("name", "Claire", "personType", "HUMAN",
                "contact", Map.of("email", "claire@example.com"))));
        post("/racks/" + rack + "/administrators", Map.of("personId", claire, "primaryAdministrator", true));

        Map<String, Object> view = api.get().uri("/views/rack-overview?name={pattern}", "VIEW-%")
                .retrieve().body(Map.class);

        assertThat(view.get("total")).isEqualTo(1);
        Map<String, Object> item = items(view).getFirst();
        assertThat(item.get("name")).isEqualTo("VIEW-R01");
        assertThat(names(item.get("children"))).containsExactly("it-srv-01");
        assertThat(item.get("administrators").toString()).contains("Claire", "claire@example.com", "primaryAdministrator=true");
    }

    @Test
    void newPrimaryAdministratorDemotesThePreviousOne() {
        String rack = id(post("/racks", Map.of("name", "IT-R02")));
        String a = id(post("/persons", Map.of("name", "A", "personType", "HUMAN")));
        String b = id(post("/persons", Map.of("name", "B", "personType", "AI")));

        post("/racks/" + rack + "/administrators", Map.of("personId", a, "primaryAdministrator", true));
        ResponseEntity<Map> second = post("/racks/" + rack + "/administrators", Map.of("personId", b, "primaryAdministrator", true));

        assertThat(second.getStatusCode().value()).isEqualTo(201);
        List<Map> links = api.get().uri("/racks/" + rack + "/administrators").retrieve().body(List.class);
        assertThat(links).extracting(l -> ((Map) l.get("person")).get("name") + "=" + l.get("primaryAdministrator"))
                .containsExactlyInAnyOrder("A=false", "B=true");
    }

    @Test
    void rackCannotBePlacedInsideItself() {
        String outer = id(post("/racks", Map.of("name", "IT-OUTER")));
        String inner = id(post("/racks", Map.of("name", "IT-INNER", "rackId", outer)));

        ResponseEntity<Map> response = put("/racks/" + inner + "/children/" + outer);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("code")).isEqualTo("dcim.containmentCycle");
    }

    @Test
    void rackWithChildrenCannotBeDeleted() {
        String rack = id(post("/racks", Map.of("name", "IT-R03")));
        post("/components", Map.of("name", "it-srv-03", "rackId", rack));

        ResponseEntity<Map> response = api.delete().uri("/racks/" + rack).retrieve().toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().get("code")).isEqualTo("dcim.rackNotEmpty");
    }

    // ---- helpers ----

    private ResponseEntity<Map> post(String path, Map<String, Object> body) {
        return api.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> put(String path) {
        return api.put().uri(path).retrieve().toEntity(Map.class);
    }

    private static String id(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().value()).as("%s", response.getBody()).isEqualTo(201);
        return (String) response.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> names(Object list) {
        return ((List<Map<String, Object>>) list).stream().map(m -> m.get("name")).toList();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null ? value : fallback;
    }
}
