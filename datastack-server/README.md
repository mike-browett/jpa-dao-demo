# datastack-server — one server, many models

A Spring Boot server that contains **no model of its own**. Every jar in [`deployments/`](deployments) is a
model, and the server hosts all of them side by side on one port and one database.

```bash
./gradlew :hogwarts-service:deploy        # copy one model into deployments/
./gradlew :dcim-service:deploy
./gradlew deploy                          # or: every model at once
./gradlew :datastack-server:bootRun       # start (restart after a deploy or undeploy)
./gradlew :dcim-service:undeploy          # remove a model
```

Deploying is per model and needs a server restart (no hot reload yet). An undeployed model's data stays in
its schema and is back when the model is redeployed.

## What each model gets

| | dcim | hogwarts |
| --- | --- | --- |
| REST | `/api/dcim/...` | `/api/hogwarts/...` |
| Views as REST | `/api/dcim/views/rack-overview` | `/api/hogwarts/views/wizard-overview` |
| GraphQL | `{ dcim { rackOverview { ... } } }` | `{ hogwarts { wizardOverview { ... } } }` |
| GraphQL types | `DcimRackOverview`, … | `HogwartsWizardOverview`, … |
| Database schema | `dcim` (its own Liquibase history) | `hogwarts` |
| Error codes | `dcim.rackNotEmpty`, … | `hogwarts.unforgivableTooWeak`, … |

The server itself adds:

- `GET /api`: the deployed models with their REST base paths.
- `{ datastackModels { id rest graphql schema } }` in GraphQL.
- One `/graphql` endpoint and GraphiQL at `/graphiql`, so a single query can read from several models:

```graphql
{
  datastackModels { id rest }
  dcim { rackOverview(size: 2) { items { name } } }
  hogwarts { houseRoster { items { name ghost } } }
}
```

## How a model plugs in

`./gradlew :<model>-service:deploy` copies the module's plain jar (generated + custom classes and resources,
without its `application.yaml`) to `deployments/<model>.jar`. On startup:

1. Spring Boot finds the model's generated `config/<Model>ModelConfiguration` through
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
2. That configuration scans the model's package with **fully qualified bean names** (two models may both have
   a `PersonService`), creates the schema and runs the model's Liquibase changelog in it, adds the model's
   `META-INF/datastack/<model>-orm.xml` to the shared persistence unit and registers the model.
3. The runtime's `DatastackAutoConfiguration` provides the base GraphQL `Query` type and adds every model's
   schema (`datastack/graphql/<model>.graphqls`, which contributes `extend type Query { <model>: ... }`).

A model running on its own (`./gradlew :hogwarts-service:bootRun`) uses exactly the same mechanism; its
application class does no component scanning.

## Run it

| Setting | Default |
| --- | --- |
| Database | `datastack` on port 15435 via `compose.yaml`; without Docker: `jdbc:postgresql://localhost:5432/datastack`, user and password `datastack`, run with `SPRING_DOCKER_COMPOSE_ENABLED=false` |
| Port | 8080 (`SERVER_PORT=8090 ./gradlew :datastack-server:bootRun`) |
| Demo data | `demo.seed: true`: models with demo data load it into an empty schema |

Packaged: `./gradlew :datastack-server:bootJar`, then from `datastack-server/`:
`java -jar build/libs/datastack-server-0.1.0-SNAPSHOT.jar`. It loads the jars in `./deployments`
(Spring Boot's `PropertiesLauncher`, `Loader-Path: deployments`). Point it elsewhere with
`-Dloader.path=/some/dir`.

## Limits of this first version

- **Restart to deploy.** Live (re)deploy without a restart is the next step: one classloader and child
  context per model, and swapping the GraphQL schema while requests are in flight.
- **Model jars carry no libraries.** The server provides Spring, JPA, GraphQL and Liquibase; a model that
  needs an extra library would need it added to the server for now.
- **Constraint names should be unique across models.** Error mapping matches PostgreSQL's constraint name,
  which does not include the schema. `uq_person_name` in two models would map to whichever model comes first.
- **One database.** All models share the server's datasource; each has its own schema.
