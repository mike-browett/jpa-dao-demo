# AixBOMS data stack — Phase 1

A small, runnable Spring Boot service that tries out the new data stack layers on one use case: **racks
that contain components and are run by administrators**.

Everything is written by hand in this phase (no code generation, no customisation yet), so each layer can
be read, debugged and judged on its own:

| Layer | What you will find |
| --- | --- |
| Database | Liquibase changelog: tables, foreign keys, constraints |
| Persistence | JPA entities `Component`, `Rack`, `Person`, `RackAdministrator` |
| DAO | One DAO per entity; every write goes through it |
| Business logic | `@Rule` classes that run on DAO events |
| View | A GraphQL view (racks + children + administrators), fetched in 4 SQL queries |
| Service | Transactional use cases |
| REST | CRUD for persons, components and racks, plus relationship endpoints |

The code is split into three Gradle modules:

| Module | What it is |
| --- | --- |
| `datastack-runtime` | The reusable framework: DAO base class, `@Rule` and the rule dispatcher, REST error handling |
| `dcim-demo` | The Spring Boot demo: the Rack / Component / Person microservice built on the runtime |
| `datastack-generator` | The future code generator. Today it reads a `model.json` and validates it |

How the pieces fit together is explained in [ARCHITECTURE.md](ARCHITECTURE.md).

---

## Start the demo

### What you need

- **JDK 21** (for example [Eclipse Temurin](https://adoptium.net)). Check with `java -version`.
- **Docker** (Docker Desktop on Windows or macOS). Used only to run PostgreSQL for you.
  No Docker? See [Without Docker](#without-docker).

You do **not** need to install Gradle: the project comes with the Gradle wrapper (`gradlew`).

### Run it

```bash
./gradlew bootRun          # Windows: gradlew.bat bootRun
```

This runs the `dcim-demo` module (same as `./gradlew :dcim-demo:bootRun`). The first run downloads Gradle and
the dependencies, so give it a few minutes. Then:

1. Spring Boot starts PostgreSQL in Docker from `dcim-demo/compose.yaml` and connects to it.
2. Liquibase creates the tables.
3. Demo data is added: 3 racks, 5 components, 3 persons.

You are ready when the log shows `Started Phase1Application`. The API runs on **http://localhost:8080**.

Stop it with `Ctrl+C`. The database container keeps running, so your data is still there next time.

### Try it

**The rack list**, as the UI would get it (racks with their children and administrators):

```bash
curl http://localhost:8080/api/views/rack-overview
```

**Step-by-step walk-through:** open [`requests.http`](requests.http) in VS Code with the
*REST Client* extension (or in IntelliJ) and click *Send Request* on each block. It creates a person, a
rack and a server, adds administrators, shows the business rules at work, and triggers the error cases.

**Postman:** import [`test/postman/aixboms-phase1.postman_collection.json`](test/postman/README.md) and run
the whole collection: 48 requests that test views, GraphQL, CRUD, relationships, rules and error codes.

**GraphQL in the browser:** open http://localhost:8080/graphiql and run, for example:

```graphql
{
  racks(size: 5) {
    name
    children { name }
    administrators { primaryAdministrator person { name personType } }
  }
}
```

---

## The REST API

All paths start with `/api`. Request and response bodies are JSON.

| Method | Path | What it does |
| --- | --- | --- |
| GET | `/views/rack-overview?page&size&name` | Racks with children and administrators (the UI's list) |
| GET, POST | `/persons` | List, create persons |
| GET, PATCH, DELETE | `/persons/{id}` | Read, update, delete a person |
| GET, POST | `/racks` | List (optionally `?name=LYO%25`), create racks |
| GET, PATCH, DELETE | `/racks/{id}` | Read, update, delete a rack (refused while it has children) |
| GET, POST | `/components` | List all components (racks included), create one (optionally inside a rack) |
| GET, PATCH, DELETE | `/components/{id}` | Read, update, delete a component |
| PUT, DELETE | `/racks/{rackId}/children/{componentId}` | Put a component into a rack, take it out |
| GET, POST | `/racks/{rackId}/administrators` | List, add administrators |
| PATCH, DELETE | `/racks/{rackId}/administrators/{personId}` | Change the primary flag, remove an administrator |

Lists are paged with `page` (from 0) and `size` (max 100).

**Updates** are partial: fields you leave out stay unchanged. Send the `version` you read to make sure
nobody changed the object in the meantime.

**Errors** come back as `application/problem+json`, always with a stable `code`:

| Situation | Status | `code` |
| --- | --- | --- |
| Invalid request body | 400 | `validation` |
| Object does not exist | 404 | `notFound` |
| Stale `version` | 409 | `concurrentModification` |
| Deleting a rack that still has children | 409 | `dcim.rackNotEmpty` |
| Same person added twice to a rack | 409 | `dcim.duplicateAdministrator` |
| Putting a rack inside itself | 422 | `dcim.containmentCycle` |

---

## Run the tests

```bash
./gradlew test                          # all modules
./gradlew :dcim-demo:test               # one module
```

- **Generator tests** (`ModelValidatorTest`) load the sample `dcim` model and check that broken models are
  reported.
- **Rule tests** (`NoContainmentCycleTest`, `SinglePrimaryAdministratorTest`) are plain unit tests with
  no Spring and no database. They take milliseconds.
- **`RackApiIntegrationTest`** goes through the real REST API against a real PostgreSQL. It starts its own
  throw-away database with Testcontainers when Docker is running, and is skipped otherwise. To run it
  against your own **empty** test database instead (it is wiped first):

  ```bash
  PHASE1_TEST_DB_URL=jdbc:postgresql://localhost:5432/phase1_test ./gradlew test
  ```

---

## Without Docker

Use any PostgreSQL 14 or newer.

1. Create a user and a database:

   ```sql
   CREATE USER phase1 PASSWORD 'phase1';
   CREATE DATABASE phase1 OWNER phase1;
   ```

2. Start the app with Docker Compose switched off:

   ```bash
   # Linux / macOS
   SPRING_DOCKER_COMPOSE_ENABLED=false ./gradlew bootRun

   # Windows PowerShell
   $env:SPRING_DOCKER_COMPOSE_ENABLED="false"; .\gradlew.bat bootRun
   ```

The connection defaults to `jdbc:postgresql://localhost:5432/phase1` with user and password `phase1`.
Change it with the environment variables `PHASE1_DB_URL`, `PHASE1_DB_USER` and `PHASE1_DB_PASSWORD`.

---

## Configuration

Settings live in `dcim-demo/src/main/resources/application.yaml`.

| Setting | Default | Meaning |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port of the API (see below) |
| `demo.seed` | `true` | Add demo data when the database is empty |
| `spring.graphql.graphiql.enabled` | `true` | GraphiQL at `/graphiql` |
| `PHASE1_DB_URL`, `PHASE1_DB_USER`, `PHASE1_DB_PASSWORD` | local `phase1` database | Database when Docker Compose is off |

### Change the server port

The API runs on port 8080. To use another port, for example 8081:

```bash
# For one run
./gradlew bootRun --args='--server.port=8081'

# With an environment variable (Linux / macOS)
SERVER_PORT=8081 ./gradlew bootRun

# With an environment variable (Windows PowerShell)
$env:SERVER_PORT="8081"; .\gradlew.bat bootRun
```

To change it permanently, add this to `dcim-demo/src/main/resources/application.yaml`:

```yaml
server:
  port: 8081
```

`server.port=0` picks a free port; the log line `Tomcat started on port …` shows which one.

With another port, adjust the clients too:

- `requests.http`: first line, `@api = http://localhost:8081/api`
- Postman: collection variable `baseUrl`, or `npx newman run … --env-var baseUrl=http://localhost:8081`
- GraphiQL: `http://localhost:8081/graphiql`

**Start from scratch:** stop the app, run `docker compose -f dcim-demo/compose.yaml down -v` to remove the
database container and its data, and start again.

**See the SQL:** `./gradlew bootRun --args='--logging.level.org.hibernate.SQL=debug'`.

---

## Project layout

```text
settings.gradle                  the three modules
build.gradle                     settings shared by all modules (Java 21, tests)
gradle/libs.versions.toml        versions (Spring Boot, plugins)

datastack-runtime/               reusable framework, no Spring Boot
  src/main/java/com/aixpertsoft/datastack/
    dao/                         Dao, JpaDao (raises rule events), NotFoundException
    rules/                       @Rule, RuleDispatcher, BeforeSave, RuleViolation
    web/                         error handling (problem+json with codes)

dcim-demo/                       the Spring Boot demo
  compose.yaml                   PostgreSQL for the demo
  src/main/java/com/aixpertsoft/
    Phase1Application.java       entry point
    dcim/
      entity/  dao/  rules/  service/  api/  rest/  view/  demo/
  src/main/resources/
    application.yaml
    db/changelog/phase1.sql      Liquibase changelog
    graphql/schema.graphqls      GraphQL schema for views
    graphql-documents/           stored view queries (RackOverview.graphql)
  src/test/java/...              rule unit tests, API integration test

datastack-generator/             model.json -> (later) generated code
  src/main/java/.../generator/   Main, ModelLoader, ModelValidator, model records
  src/test/resources/dcim/       sample model of this demo

test/postman/                    Postman collection testing the running API
requests.http                    walk-through of the API
```

### The generator

```bash
./gradlew :datastack-generator:run                                   # validates the sample dcim model
./gradlew :datastack-generator:run --args="path/to/model.json"       # any model
```

It prints a summary of objects, relations and views, and lists every problem it finds (unknown objects in
relations, missing discriminator values, unknown attribute types, missing view query files, …).

---

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `Unable to start docker compose` or Docker errors at start-up | Start Docker Desktop, or follow [Without Docker](#without-docker) |
| Port 15432 already in use | Change the host port in `dcim-demo/compose.yaml` |
| Port 8080 already in use | Use another port, see [Change the server port](#change-the-server-port) |
| `Unsupported class file major version` | Gradle is running on an old JDK: point `JAVA_HOME` at JDK 21 |
