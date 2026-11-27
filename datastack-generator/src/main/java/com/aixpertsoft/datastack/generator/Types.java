package com.aixpertsoft.datastack.generator;

import com.aixpertsoft.datastack.generator.model.AttributeDef;

/** Attribute type -> Java, SQL and GraphQL types. */
public final class Types {

    private Types() {
    }

    /** Simple Java type name; enums and JSON objects get generated types named by the caller. */
    public static String java(String type) {
        return switch (type) {
            case "string" -> "String";
            case "integer" -> "Integer";
            case "long" -> "Long";
            case "decimal" -> "java.math.BigDecimal";
            case "boolean" -> "Boolean";
            case "date" -> "java.time.LocalDate";
            case "timestamp" -> "java.time.OffsetDateTime";
            case "uuid" -> "java.util.UUID";
            default -> throw new IllegalArgumentException("No Java type for " + type);
        };
    }

    public static String sql(AttributeDef a) {
        return switch (a.type()) {
            case "string" -> "varchar(" + (a.length() != null ? a.length() : 255) + ")";
            case "integer" -> "integer";
            case "long" -> "bigint";
            case "decimal" -> "numeric";
            case "boolean" -> "boolean";
            case "date" -> "date";
            case "timestamp" -> "timestamptz";
            case "uuid" -> "uuid";
            case "enum" -> "varchar(" + Math.max(10, a.values().stream().mapToInt(String::length).max().orElse(10)) + ")";
            default -> throw new IllegalArgumentException("No SQL type for " + a.type());
        };
    }

    public static String graphql(String type) {
        return switch (type) {
            case "string", "long", "decimal", "date", "timestamp" -> "String";
            case "integer" -> "Int";
            case "boolean" -> "Boolean";
            case "uuid" -> "ID";
            default -> throw new IllegalArgumentException("No GraphQL type for " + type);
        };
    }

    public static String sqlLiteral(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return "'" + value.toString().replace("'", "''") + "'";
    }

    public static String javaLiteral(String type, Object value) {
        return switch (type) {
            case "string" -> "\"" + value.toString().replace("\"", "\\\"") + "\"";
            case "integer", "boolean" -> value.toString();
            case "long" -> value + "L";
            default -> null;
        };
    }
}
