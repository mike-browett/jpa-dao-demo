package com.aixpertsoft.datastack.generator.gen;

import java.util.TreeSet;

/** Builds the text of one Java file: imports are collected, then rendered above the body. */
public final class Src {

    private final TreeSet<String> imports = new TreeSet<>();
    private final StringBuilder body = new StringBuilder();
    private final String pkg;

    public Src(String pkg) {
        this.pkg = pkg;
    }

    public Src imports(String... classes) {
        for (String c : classes) {
            String owner = c.substring(0, c.lastIndexOf('.'));
            if (!owner.equals(pkg) && !owner.equals("java.lang")) imports.add(c);
        }
        return this;
    }

    public Src line(String text) {
        body.append(text).append('\n');
        return this;
    }

    public Src line() {
        body.append('\n');
        return this;
    }

    /** Uses a type by fully qualified name: imports it and returns the simple name. */
    public String type(String qualified) {
        if (!qualified.contains(".")) return qualified;
        imports(qualified);
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    public String render(String header) {
        StringBuilder out = new StringBuilder();
        imports.forEach(i -> out.append("import ").append(i).append(";\n"));
        if (!imports.isEmpty()) out.append('\n');
        out.append(header).append(body);
        return out.toString();
    }
}
