package com.aixpertsoft.datastack.boot;

/**
 * A generated model deployed in this application. Each model's generated configuration registers one,
 * so a server can list what it hosts: GET /api and the GraphQL field datastackModels.
 *
 * @param id      model id, also its namespace: REST /api/{id}, GraphQL Query.{id}, database schema {id}
 * @param version model version from model.json
 * @param rest    REST base path, e.g. /api/hogwarts
 * @param graphql GraphQL query field holding the model's views, e.g. hogwarts
 * @param schema  database schema of the model's tables
 */
public record DatastackModel(String id, String version, String rest, String graphql, String schema) {
}
