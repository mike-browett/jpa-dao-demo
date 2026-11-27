# dcim-service — the Rack microservice, generated from a model

The same Rack / Component / Person service as `dcim-demo`, but most of the code is **generated** from
[`model/model.json`](model/model.json). Only the business rules and a few custom bits are written by hand.

| Source of truth | For | Where |
| --- | --- | --- |
| **The model** | Tables, entities, relations, DTOs, CRUD, relationship endpoints, views, GraphQL schema | `model/model.json` |
| **Custom code** | Business rules, custom queries, extra endpoints, overrides | `src/main/java`, `src/main/resources` |
| **Generated code** | Everything derived from the model | `build/generated/datastack` (never edited, not committed) |

## Run it

```bash
./gradlew :dcim-service:bootRun          # starts PostgreSQL from compose.yaml (port 15433), generates, compiles, runs
curl http://localhost:8080/api/views/rack-overview
```

Without Docker, create a database `dcim` (user and password `dcim`) and run with
`SPRING_DOCKER_COMPOSE_ENABLED=false`. To run it next to `dcim-demo`, give one of them another port
(`--args='--server.port=8081'`).

## The development loop

1. **Edit** `model/model.json`: add an attribute, a relation, a view, …
2. **Generate**: `./gradlew :dcim-service:generateCode`, or just build or run; `compileJava` depends on it.
3. **Compile**: the generated code is compiled together with your custom code. If a model change breaks a
   rule or a custom method (an attribute renamed, a relation removed), **the Java compiler reports it** in the
   custom class that uses it.

The generator first validates the model and stops with a clear list of problems: unknown objects, view fields
that do not exist, links without `:parentIds`, and so on.

In IntelliJ, run `generateCode` once (Gradle tool window → `dcim-service` → `datastack` → `generateCode`) and
reload the Gradle project. `build/generated/datastack/java` is then a source folder and everything resolves.

## Generated vs. owned files

The generator uses the **generation gap** pattern. For every class that may need custom code it writes two
classes:

```text
build/generated/datastack/java/.../entity/RackBase.java      generated: fields, getters, setters      rewritten every build
src/main/java/.../entity/Rack.java                            owned: extends RackBase                  created once, then yours
```

The same pattern covers DAOs (`RackDaoBase` / `RackDao`), services (`RackServiceBase` / `RackService`) and REST
controllers (`RackControllerBase` / `RackController`). Everything else is generated only.

**Generated on every build** (`build/generated/datastack`, wiped first, so nothing stale survives):

| What | Files |
| --- | --- |
| Liquibase changelog | `resources/db/changelog/generated/dcim.sql`: tables, checks, unique and exclusion constraints, FKs, indexes |
| JPA mapping | `resources/META-INF/orm.xml` (entities, tables, inheritance, discriminators) and `entity/*Base.java` (fields) |
| JSON attributes, enums | `entity/PersonExt.java` (content of the `jsonb` column), `PersonContact.java`, `PersonType.java` |
| DAOs | `dao/*DaoBase.java`: CRUD plus `findByRack`, `findByRackIds`, `countByRack`, link lookups |
| API records | `api/*Dto.java`, `api/*Requests.java` (with validation from the model) |
| Services | `service/*ServiceBase.java`: CRUD, delete checks, relationship operations, error codes |
| REST | `rest/*ControllerBase.java`: CRUD and relationship endpoints |
| Views | `view/*`: records per graph node, JPQL view service, GraphQL resolvers, `ViewsController` |
| GraphQL | `resources/graphql/dcim.graphqls`, `resources/graphql-documents/RackOverview.graphql` |
| Error codes | `config/DcimConstraints.java`: database constraint → error code |

**Created once, then yours** (committed, never overwritten; delete a file to get a fresh one):

| File | Use it for |
| --- | --- |
| `entity/Component.java`, `Rack.java`, … | Derived values, convenience methods |
| `dao/*Dao.java` | Custom queries (see `RackAdministratorDao.findByRackAndPrimary`) |
| `service/*Service.java` | Overriding or adding use cases |
| `rest/*Controller.java` | Extra endpoints (paths start at the generated mapping) |
| `DcimApplication.java`, `application.yaml` | Application settings |
| `db/changelog/db.changelog-master.yaml`, `custom.sql` | Hand-written changesets |

**Fully custom** (the generator does not know about them):

- `rules/SinglePrimaryAdministrator.java`, `rules/NoContainmentCycle.java`: `@Rule` classes
- `demo/DemoData.java`: demo data created through the generated services

Entity classes you own carry **no JPA annotations**: tables, inheritance and discriminators come from the
generated `orm.xml`. That way a model change never leaves an owned class out of date.

## What the model describes

```text
objects      Component (table component, single-table inheritance), Rack extends Component, Person
relations    RackChildren       Rack.children 1-to-N Component.rack     -> FK, PUT/DELETE /racks/{id}/children/{componentId}
             RackAdministrator  Rack.administrators M-to-N Person         -> intersection entity, GET/POST/PATCH/DELETE /racks/{id}/administrators
views        RackOverview       Rack + children + administrators.person -> GraphQL type, /api/views/rack-overview
```

A **view** is a root JPQL query plus links:

- A **query link** (`"query"` + `"parentKey"`) runs one JPQL query for all parents at once (`:parentIds`),
  so N+1 is designed out.
- A **path link** (`"path"`) follows a to-one reference that the parent's query already fetched
  (`join fetch`).

`RackOverview` costs 4 SQL statements whatever the page size: count, racks, administrators with their persons,
children.

## Error codes

Derived from the model's names, so they are the same as in the hand-written demo:

| Situation | Status | Code |
| --- | --- | --- |
| Delete a rack with children | 409 | `dcim.rackNotEmpty` |
| Take out a component that is not in the rack | 404 | `dcim.notInRack` |
| Link the same person twice | 409 | `dcim.duplicateAdministrator` |
| Unlink a person who is not linked | 404 | `dcim.notAnAdministrator` |
| Duplicate serial number | 409 | `duplicateSerialNumber` |
| Rule `NoContainmentCycle` | 422 | `dcim.containmentCycle` |

## Prototype limitations

- **Schema changes:** the generated changelog describes the schema from scratch. After changing tables in
  the model, reset the development database (`docker compose -f dcim-service/compose.yaml down -v`) or add a
  hand-written changeset to `custom.sql`. Generating diff changesets comes later.
- No customer projects or `extends` between microservice projects yet (Phase 2).
- Only single-table inheritance; only PostgreSQL.
- Many-to-many from an object to itself is not supported yet.
