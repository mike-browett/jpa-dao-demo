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
                      "from": { "object": "Rack", "name": "admins" }, "to": { "object": "User", "name": "racks" } } ],
                  "views": [ { "id": "Overview", "query": "views/Missing.graphql" } ] }""");

        List<String> problems = validator.validate(loader.load(dir.resolve("model.json")), dir);

        assertThat(problems).containsExactlyInAnyOrder(
                "object Rack: extends unknown object Componnent",
                "object Person, attribute kind: enum needs \"values\"",
                "object Person, attribute name: unknown type \"text\"",
                "relation Admins: \"to\" refers to unknown object User",
                "relation Admins: manyToMany needs an \"intersection\"",
                "view Overview: query file views/Missing.graphql not found");
    }
}
