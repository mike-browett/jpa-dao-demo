package com.aixpertsoft.datastack.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.aixpertsoft.datastack.generator.gen.Output;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorTest {

    @Test
    void ownedFilesAreCreatedOnceAndNeverOverwritten(@TempDir Path dir) throws IOException, URISyntaxException {
        Path model = Path.of(getClass().getResource("/dcim/model.json").toURI());
        Output first = output(dir);
        new Generator(new ModelLoader().load(model), first).run();

        Path rack = dir.resolve("owned-java/com/aixpertsoft/dcim/entity/Rack.java");
        assertThat(rack).exists();
        Files.writeString(rack, Files.readString(rack).replace("}", "    public int custom() { return 1; }\n}"));

        Output second = output(dir);
        new Generator(new ModelLoader().load(model), second).run();

        assertThat(Files.readString(rack)).contains("public int custom()");
        assertThat(second.created()).isEmpty();
        assertThat(second.kept()).isEqualTo(first.created().size());
        assertThat(second.written()).hasSameSizeAs(first.written());
    }

    @Test
    void generatesEveryLayer(@TempDir Path dir) throws URISyntaxException {
        Path model = Path.of(getClass().getResource("/dcim/model.json").toURI());
        new Generator(new ModelLoader().load(model), output(dir)).run();

        Path java = dir.resolve("generated-java/com/aixpertsoft/dcim");
        assertThat(java.resolve("entity/RackBase.java")).exists();
        assertThat(java.resolve("dao/RackAdministratorDaoBase.java")).exists();
        assertThat(java.resolve("api/ComponentDto.java")).exists();
        assertThat(java.resolve("service/RackServiceBase.java")).exists();
        assertThat(java.resolve("rest/RackControllerBase.java")).exists();
        assertThat(java.resolve("view/RackOverviewViewService.java")).exists();
        assertThat(java.resolve("config/DcimModelConfiguration.java")).exists();
        assertThat(dir.resolve("generated-resources/META-INF/datastack/dcim-orm.xml")).exists();
        assertThat(dir.resolve("generated-resources/db/changelog/dcim/generated.sql")).exists();
        assertThat(dir.resolve("generated-resources/datastack/graphql/dcim.graphqls")).exists();
        assertThat(dir.resolve("generated-resources/META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports")).content()
                .contains("com.aixpertsoft.dcim.config.DcimModelConfiguration");
    }

    @Test
    void namespacesEverythingWithTheModelId(@TempDir Path dir) throws Exception {
        Path model = Path.of(getClass().getResource("/dcim/model.json").toURI());
        new Generator(new ModelLoader().load(model), output(dir)).run();

        Path res = dir.resolve("generated-resources");
        assertThat(res.resolve("db/changelog/dcim/generated.sql")).content()
                .contains("CREATE TABLE dcim.component").doesNotContain("CREATE TABLE component");
        assertThat(res.resolve("META-INF/datastack/dcim-orm.xml")).content().contains("<table name=\"rack_administrator\" schema=\"dcim\"/>");
        assertThat(res.resolve("datastack/graphql/dcim.graphqls")).content()
                .contains("extend type Query").contains("dcim: DcimQuery!").contains("type DcimRackOverview ")
                .doesNotContain("\ntype Query");
        Path java = dir.resolve("generated-java/com/aixpertsoft/dcim");
        assertThat(java.resolve("rest/RackControllerBase.java")).content().contains("@RequestMapping(\"/api/dcim/racks\")");
        assertThat(java.resolve("view/DcimViewsController.java")).content().contains("@RequestMapping(\"/api/dcim/views\")");
    }

    private static Output output(Path dir) {
        return new Output(dir.resolve("generated-java"), dir.resolve("generated-resources"),
                dir.resolve("owned-java"), dir.resolve("owned-resources"));
    }
}
