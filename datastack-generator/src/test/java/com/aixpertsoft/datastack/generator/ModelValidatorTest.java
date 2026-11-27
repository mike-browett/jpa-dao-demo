package com.aixpertsoft.datastack.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.aixpertsoft.datastack.generator.model.Model;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelValidatorTest {

    private final ModelLoader loader = new ModelLoader();
    private final ModelValidator validator = new ModelValidator();

    @Test
    void dcimSampleModelIsValid() throws URISyntaxException {
        Path file = Path.of(getClass().getResource("/dcim/model.json").toURI());

        Model model = loader.load(file);

        assertThat(model.objects()).extracting(o -> o.id()).containsExactly("Component", "Rack", "Person");
        assertThat(model.relations()).extracting(r -> r.id()).containsExactly("RackChildren", "RackAdministrator");
        assertThat(model.views()).extracting(v -> v.id()).containsExactly("RackOverview");
        assertThat(validator.validate(model, file.getParent())).isEmpty();
    }

    @Test
    void includedFilesAreMerged(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("model.json"), """
                { "id": "m", "javaPackage": "x", "include": ["parts/*.json"],
                  "objects": [ { "id": "A", "table": "a" } ] }""");
        Files.createDirectory(dir.resolve("parts"));
        Files.writeString(dir.resolve("parts/b.json"), """
                { "objects": [ { "id": "B", "table": "b" } ],
                  "relations": [ { "id": "AB", "type": "oneToMany",
                                   "from": { "object": "A", "name": "bs" }, "to": { "object": "B", "name": "a" } } ] }""");

        Model model = loader.load(dir.resolve("model.json"));

        assertThat(model.objects()).extracting(o -> o.id()).containsExactly("A", "B");
        assertThat(validator.validate(model, dir)).isEmpty();
    }

    @Test
    void brokenReferencesAreReported(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("model.json"), """
                { "id": "m", "javaPackage": "x",
                  "objects": [
                    { "id": "Rack", "extends": "Componnent", "discriminatorValue": "RACK" },
                    { "id": "Person", "table": "person",
                      "attributes": [ { "name": "kind", "type": "enum" }, { "name": "name", "type": "text" } ] } ],
                  "relations": [
                    { "id": "Admins", "type": "manyToMany",
                      "from": { "object": "Rack", "name": "admins" }, "to": { "object": "User", "name": "racks" } } ] }""");

        List<String> problems = validator.validate(loader.load(dir.resolve("model.json")), dir);

        assertThat(problems).containsExactlyInAnyOrder(
                "object Rack: extends unknown object Componnent",
                "object Person, attribute kind: enum needs \"values\"",
                "object Person, attribute name: unknown type \"text\"",
                "relation Admins: \"to\" refers to unknown object User",
                "relation Admins: manyToMany needs an \"intersection\"");
    }

    @Test
    void brokenViewsAreReported(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("model.json"), """
                { "id": "m", "javaPackage": "x",
                  "objects": [
                    { "id": "Rack", "table": "rack", "attributes": [ { "name": "name", "type": "string" } ] },
                    { "id": "Server", "table": "server", "attributes": [ { "name": "name", "type": "string" } ] } ],
                  "relations": [ { "id": "RackServers", "type": "oneToMany",
                                   "from": { "object": "Rack", "name": "servers" }, "to": { "object": "Server", "name": "rack" } } ],
                  "views": [ { "id": "Overview", "root": {
                      "object": "Rack", "query": "select r from Rack r", "fields": ["name", "colour"],
                      "parameters": [ { "name": "nameLike", "type": "string" } ],
                      "links": [
                        { "name": "servers", "object": "Server", "query": "select s from Server s", "parentKey": "rack.id",
                          "fields": ["name"] },
                        { "name": "owner", "object": "Server", "path": "owner", "fields": ["name"] } ] } } ] }""");

        List<String> problems = validator.validate(loader.load(dir.resolve("model.json")), dir);

        assertThat(problems).containsExactlyInAnyOrder(
                "view Overview, parameter nameLike: not used in the root query",
                "view Overview (root): Rack has no attribute colour",
                "view Overview (root), link servers: query must filter on :parentIds",
                "view Overview (root), link servers: the parent needs \"id\" in its fields, to match children to parents",
                "view Overview (root), link owner: Rack.owner is not a reference to Server");
    }
}
