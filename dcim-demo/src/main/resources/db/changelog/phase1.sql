--liquibase formatted sql

--changeset phase1:1
CREATE TABLE component (
  id             uuid         PRIMARY KEY,
  type           varchar(30)  NOT NULL,
  name           varchar(120) NOT NULL,
  serial_number  varchar(60)  UNIQUE,
  height_units   integer      CHECK (height_units BETWEEN 1 AND 60),
  rack_id        uuid         REFERENCES component(id) ON DELETE RESTRICT,
  version        bigint       NOT NULL DEFAULT 0
);
CREATE INDEX ix_component_rack ON component(rack_id);
CREATE INDEX ix_component_type ON component(type);

--changeset phase1:2
CREATE TABLE person (
  id           uuid         PRIMARY KEY,
  name         varchar(120) NOT NULL,
  person_type  varchar(10)  NOT NULL CHECK (person_type IN ('HUMAN', 'AI')),
  ext          jsonb        NOT NULL DEFAULT '{}'::jsonb,
  version      bigint       NOT NULL DEFAULT 0
);

--changeset phase1:3
CREATE TABLE rack_administrator (
  id                     uuid    PRIMARY KEY,
  rack_id                uuid    NOT NULL REFERENCES component(id) ON DELETE CASCADE,
  person_id              uuid    NOT NULL REFERENCES person(id)    ON DELETE CASCADE,
  primary_administrator  boolean NOT NULL DEFAULT false,
  version                bigint  NOT NULL DEFAULT 0,
  CONSTRAINT uq_rack_administrator UNIQUE (rack_id, person_id)
);
-- At most one primary administrator per rack. An exclusion constraint instead of a partial unique
-- index because it can be DEFERRABLE: it is checked at commit, so demoting the old primary and
-- promoting the new one can happen in any order inside the transaction.
ALTER TABLE rack_administrator
  ADD CONSTRAINT ex_rack_primary_administrator
  EXCLUDE USING btree (rack_id WITH =) WHERE (primary_administrator)
  DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX ix_rack_administrator_person ON rack_administrator(person_id);
