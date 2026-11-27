# Architecture — AixBOMS data stack, Phase 1

Phase 1 implements the target layering by hand for one use case, so that every layer can be tried out
before anything is generated. This document explains the layers, how a request flows through them, and what
changes in later phases.

**In Phase 1:** JPA, DAOs, business rules, GraphQL views, services and REST, all hand-written, on Spring Boot
and PostgreSQL.

**Not yet:** code generation from a model file, generation gap, customer projects, live deployment, the
VS Code designer, and the WildFly / JAX-RS adapter.

---

## 1. The use case

```mermaid
classDiagram
    Component <|-- Rack : is a
    Rack "0..1" <-- "0..N" Component : rack / children
    Rack "1" -- "0..N" RackAdministrator : administrators
    RackAdministrator "0..N" -- "1" Person : person
    class Component {
      UUID id
      String type
      String name
      String serialNumber
    }
    class Rack {
      Integer heightUnits
    }
    class RackAdministrator {
      boolean primaryAdministrator
    }
    class Person {
      String name
      PersonType personType
      PersonContact contact (JSON)
    }
```

- **Component / Rack:** single-table inheritance in table `component`, with discriminator `type`
  (`COMPONENT`, `RACK`). Because a Rack is a Component, racks can sit inside racks.
- **RackChildren (1-to-N):** `component.rack_id` points at the rack. A rack with children cannot be deleted.
- **RackAdministrator (M-to-N with an attribute):** intersection table `rack_administrator` with
  `primary_administrator`. A rack has at most one primary administrator.
- **Person.contact** is a **JSON attribute**, stored in the dedicated `jsonb` column `ext`. Humans fill
  `email` and `phone`; AI agents fill `endpoint`.

---

## 2. Layers

```mermaid
flowchart TB
    client([UI / curl / requests.http])
    subgraph REST["REST layer · dcim.rest, dcim.view.ViewController"]
      rc[PersonController · ComponentController · RackController]
      vc[ViewController]
    end
    subgraph SVC["Service layer · dcim.service"]
      s[PersonService · ComponentService · RackAdministratorService]
    end
    subgraph VIEW["View layer · dcim.view"]
      gql[GraphQL schema + RackGraphQlController]
      vs[RackViewService]
    end
    subgraph DAO["DAO layer · datastack.dao, dcim.dao"]
      d[JpaDao → ComponentDao · RackDao · PersonDao · RackAdministratorDao]
    end
    subgraph RULES["Business logic · datastack.rules, dcim.rules"]
      disp[RuleDispatcher]
      r1[SinglePrimaryAdministrator]
      r2[NoContainmentCycle]
    end
    subgraph JPA["Persistence · dcim.entity"]
      e[Component · Rack · Person · RackAdministrator]
    end
    db[(PostgreSQL · Liquibase changelog)]

    client -->|writes| rc --> s -->|create / update / delete| d
    client -->|reads| vc --> gql --> vs --> d
    d -->|BEFORE_INSERT / UPDATE / DELETE| disp --> r1 & r2
    d --> e --> db
```

| Layer | Package | Responsibility | Depends on |
| --- | --- | --- | --- |
| REST | `dcim.rest`, `dcim.view.ViewController` | HTTP mapping only: paths, status codes, validation of request bodies | Service, View |
| Service | `dcim.service` | Use cases; owns the transaction; returns DTOs, never entities | DAO |
| View | `dcim.view` | Read model: GraphQL schema, batched data access, views served as REST | DAO |
| DAO | `datastack.dao`, `dcim.dao` | The only way to write an entity; raises rule events; queries | JPA, rules |
| Business logic | `datastack.rules`, `dcim.rules` | `@Rule` classes reacting to DAO events | DAO (to look things up) |
| Persistence | `dcim.entity` | JPA mapping of tables, relations, JSON attributes | — |
| Database | `db/changelog/phase1.sql` | Tables, keys, constraints; Liquibase applies it at start-up | — |

`com.aixpertsoft.datastack` is the **framework**: it knows nothing about racks. `com.aixpertsoft.dcim` is
the **microservice** built on it. That split is the future boundary between the shared runtime library and
generated or hand-written microservice code.

---

## 3. Write path: adding an administrator

`POST /api/racks/{rackId}/administrators {"personId": …, "primaryAdministrator": true}`

```mermaid
sequenceDiagram
    participant C as Client
    participant R as RackController
    participant S as RackAdministratorService
    participant D as RackAdministratorDao
    participant X as RuleDispatcher
    participant Rule as SinglePrimaryAdministrator
    participant DB as PostgreSQL

    C->>R: POST administrators
    R->>S: link(rackId, personId, true)
    Note over S: @Transactional starts
    S->>D: find(rackId, personId) — duplicate? → 409
    S->>D: create(new RackAdministrator(...))
    D->>X: fire(BEFORE_INSERT, link)
    X->>Rule: apply(BeforeSave)
    Rule->>D: findByRackAndPrimary(rackId, true)
    Rule-->>Rule: demote the old primary (managed entity)
    D->>DB: persist
    Note over S,DB: commit: INSERT + UPDATE, then the deferred constraint is checked
    S-->>R: RackAdministratorDto
    R-->>C: 201 Created
```

Points to notice:

- **Rules fire in the DAO, not in JPA listeners.** Every write path goes through a DAO, so rules run
  whatever service or endpoint caused the write. Later, jOOQ-based or non-SQL DAOs can raise the same
  events.
- **A rule refuses a change** by calling `event.reject(code, message)`. The transaction rolls back and the
  client gets `422` with that code.
- **The database is the last line of defence.** "One primary per rack" is also a PostgreSQL constraint,
  so even a bug or a direct SQL update cannot create two primaries.

---

## 4. Business rules

```java
@Rule(on = RackAdministrator.class, events = {BEFORE_INSERT, BEFORE_UPDATE}, order = 100)
public class SinglePrimaryAdministrator {
    public SinglePrimaryAdministrator(RackAdministratorDao links) { ... }   // rules are Spring beans
    public void apply(BeforeSave<RackAdministrator> event) { ... }
}
```

- The annotation is the rule's only metadata. There is no separate descriptor file.
- `RuleDispatcher` collects all `@Rule` beans, sorts them by `order` (then by name), and calls those whose
  `on` matches the entity (subclasses included) and whose `events` include the current event. It logs every
  rule at the first write, which is an easy way to see what is active.
- Events run **inside the transaction, before the SQL is sent**: `BEFORE_INSERT`, `BEFORE_UPDATE`,
  `BEFORE_DELETE`. After-commit events come later.
- Rules are tested without Spring or a database; see `SinglePrimaryAdministratorTest`, which uses an
  in-memory DAO.

### The two rules

| Rule | On | Behaviour |
| --- | --- | --- |
| `SinglePrimaryAdministrator` | `RackAdministrator` insert / update | Making someone primary demotes the current primary instead of failing |
| `NoContainmentCycle` | `Component` insert / update (racks included) | Rejects placing a rack inside itself, directly or through other racks (`dcim.containmentCycle`) |

### Deviation from the design doc: deferred constraint

The design doc enforced "one primary per rack" with a partial unique index, and had the rule flush the
demotion first. That is not enough when an existing link is *updated* to primary, because the promotion is
also pending in the same flush, and Hibernate's write order would decide whether it works.

Phase 1 uses a PostgreSQL **exclusion constraint that is `DEFERRABLE INITIALLY DEFERRED`** instead:

```sql
ALTER TABLE rack_administrator
  ADD CONSTRAINT ex_rack_primary_administrator
  EXCLUDE USING btree (rack_id WITH =) WHERE (primary_administrator)
  DEFERRABLE INITIALLY DEFERRED;
```

It is checked at commit, so the rule simply changes the flag on the old primary, with no flush and no
ordering concerns. The generator should produce this for "unique where" constraints on intersection
attributes.

---

## 5. Read path: the RackOverview view

`GET /api/views/rack-overview?page=0&size=20&name=LYO%25`

1. `ViewController` loads the stored query `graphql-documents/RackOverview.graphql` and runs it in-process
   through Spring for GraphQL. **The client never speaks GraphQL**; it gets plain JSON with
   `page`, `size`, `total` and `items`.
2. `RackGraphQlController` resolves `racks` and `rackCount` with one query each.
3. `children` and `administrators` are `@BatchMapping`s: Spring collects every rack in the response and asks
   for all their children (and all their administrators) **in one call each**.
4. `RackViewService` turns those calls into one SQL query per level, using `ComponentDao.findChildrenOfRacks`
   and `RackAdministratorDao.findByRacks` with a `join fetch` on the person.

Result: **4 SQL statements per call, whatever the page size**: count, racks, administrators with persons,
children. Check it with `--logging.level.org.hibernate.SQL=debug`.

Design choices:

- **Views return records (`ViewNodes`), never JPA entities.** No lazy loading outside a transaction, and
  no hidden N+1 queries.
- **GraphQL is the view definition language, not the write API.** Writes go through REST and the service
  layer, so they always pass through DAOs and rules.
- The same schema is available at `/graphql`, with GraphiQL at `/graphiql`, for exploring and building
  new views.
- `RackViewService` is the hand-written form of the **data-source contract** from the design doc: `racks`
  is `fetchRoots`, and `children` / `administrators` are `fetchByParentKeys`. Later, these methods are
  generated or provided by pluggable providers (JPQL, jOOQ, REST, sensors).

---

## 6. Persistence details

| Topic | Phase 1 choice |
| --- | --- |
| Schema ownership | Liquibase (`phase1.sql`); Hibernate only validates (`ddl-auto: validate`) |
| Keys | UUIDs generated by Hibernate |
| Optimistic locking | `@Version` on every entity; clients may send `version` with updates (409 if stale) |
| Inheritance | Single table with discriminator; the discriminator is also mapped read-only as `type` |
| Intersection | Always an entity (`RackAdministrator`), never `@ManyToMany`, so attributes can be added later without breaking anything |
| JSON attributes | Record `PersonExt` in `jsonb` column `ext`, mapped with `@JdbcTypeCode(SqlTypes.JSON)`; typed accessor `getContact()` |
| Open session in view | Off: entities never leave the service layer |
| Rack lookups | `RackDao.get` queries by type, so a component id passed where a rack is expected gives 404 |

---

## 7. Errors

`datastack.web.ProblemHandler` maps exceptions to `application/problem+json` with a stable `code`:

| Source | Status | Code |
| --- | --- | --- |
| `RuleViolation` from a rule | 422 | the rule's code, e.g. `dcim.containmentCycle` |
| `ApiException` from a service | as given | e.g. `dcim.rackNotEmpty`, `dcim.duplicateAdministrator` |
| `NotFoundException` from a DAO | 404 | `notFound` |
| Optimistic locking failure | 409 | `concurrentModification` |
| Bean validation | 400 | `validation` |
| Known database constraints | 409 | mapped by constraint name |

---

## 8. From Phase 1 to the target architecture

| Phase 1 (hand-written) | Later |
| --- | --- |
| Entities, DAOs, DTOs, controllers, GraphQL schema | Generated from `model.json` (generation gap: regenerated `…Base` classes plus developer-owned subclasses) |
| `phase1.sql` | Generated Liquibase changelog per model version |
| `@Rule` classes | Stay hand-written, unchanged |
| `datastack` package | Becomes the shared runtime library (`datastack-runtime.jar`) |
| `RackViewService` | Generated resolvers on the data-source contract; pluggable providers |
| `ViewController` | Generic: one endpoint per view declared in the model |
| Spring MVC on Spring Boot | Thin web adapter; a JAX-RS adapter for WildFly next to it |
| One project | Base and customer microservice projects, merged by an assembly build |

The aim of Phase 1 is to settle what the generated code should look like, by writing it by hand once. Things
worth checking while reviewing:

- Is the DAO API (`Dao`, `JpaDao`) the right contract for rules and services?
- Are `@Rule`, `BeforeSave` and `RuleDispatcher` comfortable to write and test against?
- Does the view approach (stored GraphQL query, batch mappings, records) scale to the real Verios views?
- Is the REST shape (relationship endpoints, partial updates, error codes) what the UI needs?

---

## 9. Known simplifications

- No security or ACL: all endpoints are open.
- `BeforeSave` does not yet expose old values (`changed(attribute)` from the design doc). `NoContainmentCycle`
  simply checks every save.
- Rules run synchronously; no after-commit or async events yet.
- No jOOQ yet; all access is JPA.
- No OpenAPI spec or TypeScript client yet.
- Views are hard-wired to one REST endpoint each.
