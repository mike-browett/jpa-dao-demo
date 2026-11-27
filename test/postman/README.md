# Postman tests

`aixboms-phase1.postman_collection.json` tests the running demo through its HTTP APIs:
views, GraphQL, CRUD, relationships, both business rules and the error codes.
48 requests with 93 assertions.

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
