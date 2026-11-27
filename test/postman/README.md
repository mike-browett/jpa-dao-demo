# Postman tests

| Collection | Tests | Requests / assertions |
| --- | --- | --- |
| `aixboms-phase1.postman_collection.json` | The hand-written `dcim-demo`: views, GraphQL, CRUD, relationships, both business rules and the error codes | 48 / 93 |
| `hogwarts.postman_collection.json` | The generated `hogwarts-service`, alone or in `datastack-server`. See [below](#hogwarts) | 72 / 207 |

The sections up to "What is covered" describe the Phase 1 collection.

## Import into Postman

1. Postman → **Import** → choose `aixboms-phase1.postman_collection.json`.
2. If the app does not run on `http://localhost:8080`, open the collection → **Variables** and change `baseUrl`.

## Run it

Start the demo first (`./gradlew bootRun`, see the main README).

- **In Postman:** open the collection → **Run** → keep the default order → **Run AixBOMS data stack - Phase 1**.
- **From the command line**, with [Newman](https://github.com/postmanlabs/newman):

  ```bash
  npx newman run test/postman/aixboms-phase1.postman_collection.json
  # different port:
  npx newman run test/postman/aixboms-phase1.postman_collection.json --env-var baseUrl=http://localhost:8081
  ```

Run the folders **in order**: requests save ids (`personId`, `rackAId`, …) that later requests use.
Single requests in folders 1 and 2 also work on their own.

Every run creates its own persons, racks and components (names contain a run id), and folder 7 deletes them
again, so the collection can be run any number of times and leaves the demo data untouched.

## What is covered

| Folder | Checks |
| --- | --- |
| 1 Views | `rack-overview` page shape, name filter |
| 2 GraphQL | Nested query, field selection, stored query with variables, schema validation error |
| 3 Persons | Create human and AI agent (JSON contact), read, list, partial update, stale version 409, validation 400, 404 |
| 4 Racks and components | Create, read, filter, update, duplicate serial 409, `heightUnits` on a non-rack 422, component used as rack 404 |
| 5 Rack children | Move between racks, rack inside rack, **NoContainmentCycle** 422, not in rack 404, delete non-empty rack 409 |
| 6 Rack administrators | **SinglePrimaryAdministrator** demotes the old primary (insert and update), exactly one primary, duplicate 409, view shows the result, remove, 404 |
| 7 Cleanup | Deletes everything the run created |

---

## hogwarts

`hogwarts.postman_collection.json` tests the generated Harry Potter service. It needs the demo data
(`demo.seed=true`, the default) and works against the model running on its own or deployed in
`datastack-server`:

```bash
./gradlew :hogwarts-service:bootRun                      # on its own
# or: ./gradlew :hogwarts-service:deploy && ./gradlew :datastack-server:bootRun

npx newman run test/postman/hogwarts.postman_collection.json
npx newman run test/postman/hogwarts.postman_collection.json --env-var baseUrl=http://localhost:8090
```

Variables: `baseUrl` (default `http://localhost:8080`) and `api`, the model's REST base path (`/api/hogwarts`).
As with the Phase 1 collection, run the folders in order. Each run creates its own house, spells, student and
professor (names end with a run id) and folder 8 deletes them, so it can be repeated and leaves the demo data
as it was.

| Folder | Checks |
| --- | --- |
| 1 Views | `wizard-overview` (house, wand JSON, spells with one favourite first), name filter, a professor's `subject` in the supertype view, paging, `spell-book` casters of Avada Kedavra, `house-roster` |
| 2 GraphQL | Namespaced query `{ hogwarts { … } }`, field selection, variables, many-to-many from the spell side, `datastackModels`, un-namespaced field rejected |
| 3 Houses | Create, read, list, partial update with version, stale version 409, duplicate name 409, missing name 400, 404 |
| 4 Spells | Default `unforgivable=false`, partial update without version, unknown enum 400 `unreadable`, difficulty range 400, missing category 400, duplicate incantation 409 |
| 5 Wizards and professors | Wand as JSON attribute, professor (single-table subtype) read through `/wizards`, list of professors, update subject and wand, missing subject 400, year range 400, duplicate name 409, view shows the professor |
| 6 House members | Move a wizard between houses, roster reflects it, delete non-empty house 409 `hogwarts.houseNotEmpty`, not in house 404, remove member |
| 7 Wizard spells | **SingleFavouriteSpell** on insert and update (exactly one favourite), level update, level 0/10 and missing level 400, duplicate link 409, **UnforgivableCursesNeedPower** 422 on insert and update, level 7 accepted, both views show the links, remove, 404, unknown spell 404 |
| 8 Cleanup | Deletes everything the run created; checks the demo data is untouched |
