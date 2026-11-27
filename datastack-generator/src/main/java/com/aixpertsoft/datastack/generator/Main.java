package com.aixpertsoft.datastack.generator;

import com.aixpertsoft.datastack.generator.model.Model;
import com.aixpertsoft.datastack.generator.model.ObjectDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;
import java.nio.file.Path;
import java.util.List;

/**
 * Command line entry point: {@code generator <path/to/model.json>}.
 * Phase 1 loads and validates the model and prints a summary; code generation comes next.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: generator <path/to/model.json>");
            System.exit(2);
        }
        Path modelFile = Path.of(args[0]);
        Model model = new ModelLoader().load(modelFile);
        List<String> problems = new ModelValidator().validate(model, modelFile.toAbsolutePath().getParent());

        System.out.println(summary(model));
        if (problems.isEmpty()) {
            System.out.println("OK  model is valid");
        } else {
            System.out.println(problems.size() + " problem(s):");
            problems.forEach(p -> System.out.println("  - " + p));
            System.exit(1);
        }
    }

    static String summary(Model model) {
        StringBuilder out = new StringBuilder();
        out.append("Model ").append(model.id()).append(' ').append(model.version())
           .append(" (").append(model.javaPackage()).append(")\n");
        out.append("  objects   ").append(model.objects().size()).append('\n');
        for (ObjectDef o : model.objects()) {
            out.append("    ").append(o.id())
               .append(o.extendsId() != null ? " extends " + o.extendsId() : " -> table " + o.table())
               .append(", ").append(o.attributes().size()).append(" attribute(s)\n");
        }
        out.append("  relations ").append(model.relations().size()).append('\n');
        for (RelationDef r : model.relations()) {
            out.append("    ").append(r.id()).append(": ")
               .append(r.from() != null ? r.from().object() : "?").append(' ').append(r.type()).append(' ')
               .append(r.to() != null ? r.to().object() : "?").append('\n');
        }
        out.append("  views     ").append(model.views().size());
        return out.toString();
    }
}
