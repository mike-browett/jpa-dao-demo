package com.aixpertsoft.hogwarts.rest;

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
 * End-to-end through the generated REST API against a real PostgreSQL, with the Harry Potter demo data loaded
 * (so the seed itself is checked against the rules and constraints).
 *
 * <p>Uses a throw-away PostgreSQL container when Docker is available. Without Docker, set HOGWARTS_TEST_DB_URL
 * (plus HOGWARTS_TEST_DB_USER / HOGWARTS_TEST_DB_PASSWORD) to an empty test database; it is wiped at the start.
 * With neither, the test is skipped.
 */
@SpringBootTest(classes = com.aixpertsoft.hogwarts.HogwartsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"demo.seed=true", "spring.docker.compose.enabled=false"})
@EnabledIf("databaseAvailable")
@SuppressWarnings("unchecked")
class HogwartsApiIntegrationTest {

    private static final String TEST_DB_URL = System.getenv("HOGWARTS_TEST_DB_URL");

    static boolean databaseAvailable() {
        return TEST_DB_URL != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (TEST_DB_URL != null) {
            registry.add("spring.datasource.url", () -> TEST_DB_URL);
            registry.add("spring.datasource.username", () -> env("HOGWARTS_TEST_DB_USER", "hogwarts"));
            registry.add("spring.datasource.password", () -> env("HOGWARTS_TEST_DB_PASSWORD", "hogwarts"));
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
    void wizardOverviewShowsHouseWandAndSpellsFavouriteFirst() {
        Map<String, Object> harry = only(view("wizard-overview", "Harry Potter"));

        assertThat(harry).containsEntry("type", "WIZARD").containsEntry("patronus", "Stag");
        assertThat((Map<String, Object>) harry.get("house")).containsEntry("name", "Gryffindor");
        assertThat((Map<String, Object>) harry.get("wand")).containsEntry("wood", "Holly");
        List<Map<String, Object>> spells = (List<Map<String, Object>>) harry.get("spells");
        assertThat(spells.getFirst()).containsEntry("favourite", true).containsEntry("level", 9);
        assertThat((Map<String, Object>) spells.getFirst().get("spell")).containsEntry("incantation", "Expelliarmus");
    }

    @Test
    void professorsAreWizardsWithASubject() {
        Map<String, Object> snape = only(view("wizard-overview", "Severus Snape"));
        assertThat(snape).containsEntry("type", "PROFESSOR").containsEntry("subject", "Potions");

        ResponseEntity<Map> noSubject = post("/professors", Map.of("name", "Gilderoy Lockhart"));
        assertThat(noSubject.getStatusCode().value()).isEqualTo(400);
        assertThat(noSubject.getBody()).containsEntry("code", "validation");
    }

    @Test
    void spellBookListsCastersAcrossTheIntersection() {
        Map<String, Object> avada = only(view("spell-book", "Avada Kedavra"));
        List<Map<String, Object>> casters = (List<Map<String, Object>>) avada.get("casters");
        assertThat(casters).extracting(c -> ((Map<String, Object>) c.get("wizard")).get("name"))
                .containsExactlyInAnyOrder("Bellatrix Lestrange", "Severus Snape", "Tom Riddle");
    }

    @Test
    void aNewFavouriteReplacesTheOldOne() {
        String luna = id(only(view("wizard-overview", "Luna Lovegood")));
        String reparo = id(only(view("spell-book", "Reparo")));

        ResponseEntity<Map> response = api.patch().uri("/wizards/" + luna + "/spells/" + reparo)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("favourite", true)).retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        List<Map<String, Object>> spells = api.get().uri("/wizards/" + luna + "/spells").retrieve().body(List.class);
        assertThat(spells).filteredOn(s -> Boolean.TRUE.equals(s.get("favourite")))
                .extracting(s -> ((Map<String, Object>) s.get("spell")).get("name"))
                .containsExactly("Mending Charm");
    }

    @Test
    void unforgivableCursesNeedLevelSeven() {
        String neville = id(only(view("wizard-overview", "Neville Longbottom")));
        String crucio = id(only(view("spell-book", "Crucio")));

        ResponseEntity<Map> weak = post("/wizards/" + neville + "/spells", Map.of("spellId", crucio, "level", 3));
        assertThat(weak.getStatusCode().value()).isEqualTo(422);
        assertThat(weak.getBody()).containsEntry("code", "hogwarts.unforgivableTooWeak");
    }

    @Test
    void levelIsOneToNineAndALinkIsUnique() {
        String ron = id(only(view("wizard-overview", "Ron Weasley")));
        String lumos = id(only(view("spell-book", "Lumos")));

        ResponseEntity<Map> tooHigh = post("/wizards/" + ron + "/spells", Map.of("spellId", lumos, "level", 10));
        assertThat(tooHigh.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<Map> again = post("/wizards/" + ron + "/spells", Map.of("spellId", lumos, "level", 5));
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(again.getBody()).containsEntry("code", "hogwarts.duplicateSpell");
    }

    @Test
    void aHouseWithMembersCannotBeDeletedAndUnknownEnumsAreRejected() {
        Map<String, Object> roster = api.get().uri("/views/house-roster").retrieve().body(Map.class);
        String slytherin = items(roster).stream()
                .filter(h -> "Slytherin".equals(h.get("name"))).map(h -> (String) h.get("id")).findFirst().orElseThrow();
        ResponseEntity<Map> delete = api.delete().uri("/houses/" + slytherin).retrieve().toEntity(Map.class);
        assertThat(delete.getStatusCode().value()).isEqualTo(409);
        assertThat(delete.getBody()).containsEntry("code", "hogwarts.houseNotEmpty");

        ResponseEntity<Map> prank = post("/spells", Map.of("name", "Tickling Charm", "category", "PRANK"));
        assertThat(prank.getStatusCode().value()).isEqualTo(400);
        assertThat(prank.getBody()).containsEntry("code", "unreadable");
    }

    // ---- helpers ----

    private List<Map<String, Object>> view(String path, String name) {
        return items(api.get().uri("/views/" + path + "?name={name}", name).retrieve().body(Map.class));
    }

    private ResponseEntity<Map> post(String path, Map<String, Object> body) {
        return api.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(Map.class);
    }

    private static Map<String, Object> only(List<Map<String, Object>> items) {
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private static String id(Map<String, Object> item) {
        return (String) item.get("id");
    }

    private static List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null ? value : fallback;
    }
}
