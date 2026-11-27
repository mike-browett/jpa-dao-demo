package com.aixpertsoft.datastack.generator;

import com.aixpertsoft.datastack.generator.model.Model;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/** Reads model.json and merges the files listed in "include" (glob patterns relative to the model file). */
public class ModelLoader {

    private final JsonMapper json = JsonMapper.builder().build();

    public Model load(Path modelFile) {
        Model model = read(modelFile);
        Path dir = modelFile.toAbsolutePath().getParent();
        for (String pattern : model.include()) {
            for (Path included : matching(dir, pattern)) {
                model = model.merge(read(included));
            }
        }
        return model;
    }

    private Model read(Path file) {
        try {
            return json.readValue(file.toFile(), Model.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static List<Path> matching(Path dir, String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                        .filter(f -> matcher.matches(dir.relativize(f)))
                        .sorted()
                        .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
