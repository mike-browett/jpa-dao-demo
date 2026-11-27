package com.aixpertsoft.datastack.generator;

import java.util.Locale;

/** Naming conventions shared by all generators. */
public final class Names {

    private Names() {
    }

    public static String upper(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String lower(String s) {
        return s == null || s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** serialNumber -> serial_number, RackAdministrator -> rack_administrator */
    public static String snake(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** rack-overview / rack_overview -> rackOverview */
    public static String camel(String s) {
        StringBuilder out = new StringBuilder();
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '-' || c == '_') {
                up = true;
            } else {
                out.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return lower(out.toString());
    }

    /** administrators -> administrator, children -> child */
    public static String singular(String s) {
        if (s.endsWith("ren")) return s.substring(0, s.length() - 3);
        if (s.endsWith("ies")) return s.substring(0, s.length() - 3) + "y";
        if (s.endsWith("s")) return s.substring(0, s.length() - 1);
        return s;
    }

    /** "A" or "An" before a word, for error codes like notAnAdministrator. */
    public static String article(String word) {
        return "AEIOUaeiou".indexOf(word.charAt(0)) >= 0 ? "An" : "A";
    }
}
