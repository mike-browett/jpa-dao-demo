package com.aixpertsoft.datastack.generator;

import com.aixpertsoft.datastack.generator.gen.Output;
import com.aixpertsoft.datastack.generator.model.Model;
import com.aixpertsoft.datastack.generator.model.ObjectDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command line entry point.
 * <pre>
 *   validate &lt;model.json&gt;
 *   generate --model &lt;model.json&gt; --generated-java &lt;dir&gt; --generated-resources &lt;dir&gt;
 *            --owned-java &lt;dir&gt; --owned-resources &lt;dir&gt;
 * </pre>
 * A single argument is treated as "validate".
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 1) {
            validate(Path.of(args[0]));
        } else if (args.length >= 2 && args[0].equals("validate")) {
            validate(Path.of(args[1]));
        } else if (args.length >= 1 && args[0].equals("generate")) {
            generate(options(args));
        } else {
            System.err.println("Usage: validate <model.json> | generate --model <file> --generated-java <dir> "
                    + "--generated-resources <dir> --owned-java <dir> --owned-resources <dir>");
            System.exit(2);
        }
    }

    private static Model validate(Path modelFile) {
        Model model = new ModelLoader().load(modelFile);
        List<String> problems = new ModelValidator().validate(model, modelFile.toAbsolutePath().getParent());
        System.out.println(summary(model));
        if (!problems.isEmpty()) {
            System.out.println(problems.size() + " problem(s) in " + modelFile + ":");
            problems.forEach(p -> System.out.println("  - " + p));
            System.exit(1);
        }
        System.out.println("OK  model is valid");
        return model;
    }

    private static void generate(Map<String, String> o) {
        Model model = validate(Path.of(require(o, "model")));
        Output out = new Output(Path.of(require(o, "generated-java")), Path.of(require(o, "generated-resources")),
                Path.of(require(o, "owned-java")), Path.of(require(o, "owned-resources")));
        new Generator(model, out).run();
        System.out.println("Generated " + out.written().size() + " file(s) (overwritten on every build)");
        if (!out.created().isEmpty()) {
            System.out.println("Created " + out.created().size() + " file(s) you own (never overwritten):");
            out.created().forEach(p -> System.out.println("  + " + p));
        }
        System.out.println("Kept " + out.kept() + " existing file(s) you own unchanged");
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            o.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return o;
    }

    private static String require(Map<String, String> o, String key) {
        String v = o.get(key);
        if (v == null) throw new IllegalArgumentException("Missing --" + key);
        return v;
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
