# Roadmap — AixBOMS data stack (BC4J replacement)

Where the project stands: what is **implemented**, what is **next**, what is **to do**, and the
**decisions** taken on the way. Details live in [README](README.md) and [ARCHITECTURE](ARCHITECTURE.md);
this page is the overview.

**How to keep it current:** when a piece of work is finished, move its line from *To do* to *Implemented*
in the same commit, with the commit id. New ideas go to *To do* (or *Parking lot* if undecided).

Status: ✅ done · 🔜 next · ⬜ to do · ❓ needs a decision

---

## At a glance

| Area | Status | Notes |
| --- | --- | --- |
| Target architecture and layering | ✅ | Design doc; Phase 1 written by hand to settle the shape of the code |
| Runtime (DAO, rules, errors, paging) | ✅ | `datastack-runtime` |
| Generator: model → microservice | ✅ | Liquibase, JPA, DAO, service, REST, views, GraphQL |
| Several models on one server | ✅ | `datastack-server`, per-model deploy, restart needed |
| Customisation (Phase 2) | 🔜 | Customer projects, customer attributes, Groovy rules |
| Model evolution (changes after first deploy) | ⬜ ❓ | Today the generated changelog only creates tables |
| Inter-model references (relations and view links across microservices) | ⬜ ❓ | Models are isolated today |
| Live deploy (no restart) | ⬜ | |
| Security / ACL | ⬜ | |
| Migration from BC4J (Verios) | ⬜ | Importer and pilot module |
| Designers / VS Code plugin | ⬜ | |
| CI | ⬜ | |

---

## ✅ Implemented

### Phase 1: layers written by hand (`dcim-demo`)
| What | Commit |
| --- | --- |
| Rack / Component / Person use case: JPA, DAO, `@Rule` business rules, services, REST, GraphQL view | f340b52 |
| Error model: problem+json with `code`, constraint → error code mapping, 409 on stale version | f340b52 |
| "One primary administrator per rack": deferrable exclusion constraint + `SinglePrimaryAdministrator` rule | f340b52 |
| Postman collection (48 requests) | c1739a4 |
| Split into modules: `datastack-runtime`, `dcim-demo`, `datastack-generator` | aae7968 |

### Generator (`datastack-generator`, `dcim-service`)
| What | Commit |
| --- | --- |
| `model.json` with includes, validation with readable messages | c0f33f2 |
| Liquibase changelog: tables, checks, unique and exclusion constraints, FKs, indexes | c0f33f2 |
| JPA via `orm.xml`, single-table inheritance, JSON attributes in a `jsonb` column | c0f33f2 |
| DAOs, DTOs, services, REST controllers, 1-to-N and M-to-N relations (intersection with attributes) | c0f33f2 |
| Views: JPQL root + batched links → records, GraphQL schema and resolvers, REST `/views/...` | c0f33f2 |
| Generation gap: generated `…Base` classes rebuilt every time; owned subclasses created once, never overwritten | c0f33f2 |
| `dcim-service`: the Phase 1 demo regenerated from a model, same tests pass | c0f33f2 |

### Second model (`hogwarts-service`)
| What | Commit |
| --- | --- |
| Harry Potter model: Wizard ↔ Spell with `level` 1–9 and `favourite`, Professor subtype, houses, 3 views, demo data | 6b50d45 |
| Generator fixes: validation on link requests, enum naming, subtype fields in views, `uniqueWhere` code/message, `400 unreadable` | 6b50d45 |
| Postman collection (72 requests, 207 assertions) | 807607f |

### Several models on one server
| What | Commit |
| --- | --- |
| Namespacing per model: REST `/api/<model>`, GraphQL `Query.<model>`, database schema, resources, bean names | e51a478 |
| Model as a plug-in: generated Spring Boot auto-configuration per model | e51a478 |
| `datastack-server` + `./gradlew :<model>-service:deploy` / `undeploy`; `GET /api` lists deployed models | e51a478 |

---

## 🔜 Next

| # | Item | Why now |
| --- | --- | --- |
| 1 | **Model evolution** ❓: generate incremental Liquibase changesets when the model changes (add column, new relation, rename) | Without it a model cannot change once it has data. Prerequisite for real use |
| 2 | **Phase 2: customer projects**: base + customer microservice projects merged by an assembly build | Core requirement: customise existing objects without forking |
| 3 | **Customer attributes**: declared in a customer model, as real columns or in the `jsonb` column | Same |
| 4 | **Groovy rules** next to Java `@Rule` classes | Customisation without a Java build |

---

## ⬜ To do

### Model and generator
- ⬜ Model versioning: model version recorded per deployment; changelog per version (see Next #1)
- ⬜ `BeforeSave` with old values: `changed(attribute)`, so rules can react to what changed
- ⬜ After-commit and asynchronous events (notifications, integration)
- ⬜ Inheritance strategy per hierarchy ❓: `singleTable` / `joined` / `typeAsData` for hundreds of component types (ARCHITECTURE §12)
- ⬜ View parameters beyond `nameLike`: filters, sorting chosen by the client
- ⬜ Decimal values in GraphQL come back as strings (`"lengthInches": "11"`)
- ⬜ Constraint names unique across models, or match by schema (two models with `uq_person_name`)
- ⬜ Generator as a real Gradle plugin (today a `JavaExec` task in each module)

### Inter-model references
Relations and view links that span models (microservices), e.g. a `hogwarts` Wizard linked to a `dcim` Person.
Today every model is self-contained: no relation, view link or GraphQL type can point into another model.
- ⬜ Model syntax for a reference to another model's object: `"object": "dcim:Person"`, with the referenced
  model declared as a dependency (id and version range)
- ⬜ Validation against the referenced model's published contract (its objects and keys), not its source
- ⬜ View links across models:
  - same server: resolve through the other model's view service or DAO (batched, no N+1)
  - different servers: fetch through the other model's API (REST or GraphQL), batched by ids
- ⬜ GraphQL: a node of one model exposing a field typed with another model's type (schema stitching or federation ❓)
- ⬜ Integrity ❓: database foreign key across schemas when both models share a database; otherwise a reference
  by id checked by a rule, and a policy when the referenced object is deleted (restrict, nullify, ignore)
- ⬜ Deploy dependencies: refuse to deploy a model whose referenced models are missing or incompatible
- ⬜ Writes: linking and unlinking across models through the REST API (stays an id on the referencing side)

### Runtime and deploy
- ⬜ Live deploy: one classloader and child context per model; GraphQL schema swapped without restart
- ⬜ Model jars with their own libraries (today the server provides everything)
- ⬜ JAX-RS web adapter for WildFly next to Spring MVC ❓ (still needed?)
- ⬜ Data-source contract: views reading from non-RDBMS sources; jOOQ backend next to JPA

### API and clients
- ⬜ OpenAPI spec generated from the model; TypeScript client
- ⬜ Postman collection for `dcim-service` (the Phase 1 one targets `dcim-demo`)

### Security
- ⬜ Authentication on REST and GraphQL
- ⬜ ACL: permissions per object type / object, enforced in DAO or service layer

### Migration from BC4J
- ⬜ Importer: BC4J entity and view object definitions → `model.json`
- ⬜ Pilot: one Verios module end to end on the new stack, compared with the BC4J version
- ⬜ Coexistence plan: both stacks on the same database during migration

### Tooling
- ⬜ Model designer: VS Code plugin (edit, validate, preview generated code, run)
- ⬜ CI: GitHub Actions running the build and the integration tests with Testcontainers

---

## Decisions

| Decision | Status |
| --- | --- |
| JPQL for views (not a custom query language) | ✅ decided |
| Jmix: trialled, dropped (Vaadin-centric, we do not use Vaadin) | ✅ decided |
| Spring Boot for the prototype (WildFly + JAX-RS considered) | ✅ decided for now |
| One JSON model per microservice project, split with includes | ✅ decided |
| Rules are annotated Java classes (`@Rule`); Groovy to come | ✅ decided |
| Custom code by generation gap (generated base + owned subclass) | ✅ decided |
| GraphQL for views; REST for writes | ✅ decided (value of `/views` REST endpoints questioned, kept for now) |
| Customer attributes as real columns or JSON in a `jsonb` column | ✅ decided |
| Customer extension through an assembly build | ✅ decided, not built |
| Single-table inheritance | ✅ for now, revisit with many component types |
| One schema per model, one database per server | ✅ decided |
| Incremental changelogs: generated diff vs. hand-written changesets | ❓ open |
| Live deploy vs. restart | ❓ restart accepted for now |
| Inter-model references: shared database with cross-schema keys, or API calls between services; GraphQL stitching vs. federation | ❓ open |

---

## Parking lot

Ideas not yet planned: model diff/preview before deploy, admin UI listing deployed models, metrics per model,
read replicas for views.
