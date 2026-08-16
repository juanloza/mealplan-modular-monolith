# Mealplan API

A meal planning REST API with pantry stock control, built as a **modular monolith** in Java 25 and Spring Boot 4.1. The module boundaries are not a diagram: they are ArchUnit rules that fail the build, and the stock invariant is not a comment; it is an optimistic-locking test that runs two real threads against a real PostgreSQL.

## What it does

You keep a catalog of ingredients and recipes, you track what you actually have in the pantry, and you plan meals. The interesting operation is **cooking a plan entry**: it scales the recipe from its own serving count to the planned one, and deducts the result from the pantry, all in one transaction, all or nothing. If anything is missing, you get a 409 listing every shortfall at once, and nothing is deducted.

Recipes have a lifecycle (`DRAFT -> PUBLISHED -> ARCHIVED`), only published ones can be planned, and a published recipe is immutable. Plan entries have their own (`PLANNED -> COOKED | CANCELLED`), and a cooked one can never be deleted.

Deliberately small in scope: the point is to demonstrate module boundaries, state machines, concurrency correctness and exact arithmetic, not to build a nutrition SaaS.

## Design decisions worth a look

**Quantities are integers, and they cross the wire as strings.** Every amount is a `long` counting thousandths of its dimension's canonical unit: 1 kg is `1_000_000`, half an egg is `500`. JSON carries `{"amount": "262.500", "unit": "GRAM"}` as a *string*, because a JSON number would pass through a `double` somewhere in the stack, and a pantry that drifts by rounding error is worse than one that refuses odd input. Scaling a recipe uses integer division with explicit HALF_UP rounding, applied **per line, never to a total**. The rounded number has to be the same number that leaves the pantry row.

**Two different races get two different guards, and neither replaces the other.** Cooking the same plan entry twice concurrently is guarded by a `@Version` on the plan entry; cooking two *different* entries that share an ingredient is guarded by a `@Version` on the pantry row. The case that makes the first one load-bearing is cook-versus-cancel: without it, you could end up with a cancelled entry whose ingredients were already deducted. Both are proven by a test that starts real threads and coordinates them with a barrier.

**Optimistic locking with no automatic retry: a conflict returns 409.** A retry would be *correct* (the transaction rolls back entirely and would re-read), but it would return success computed over numbers the client never saw, for an operation that is not idempotent. Pessimistic `SELECT ... FOR UPDATE` was rejected for the same reason, plus it would pay the locking cost on every cook to handle a conflict that almost never happens. This also means `READ COMMITTED` is enough: the `WHERE version = ?` turns a lost update into zero rows affected instead of silent overwrite.

**Ownership checks live in the service, not in `@PreAuthorize`.** The filter chain answers "is anyone authenticated?", a property of the request. "Does this user own this resource?" requires loading the aggregate, so it belongs to the code that already loads it. `@PreAuthorize`, `@Secured` and `@RolesAllowed` appear nowhere in this codebase, and an ArchUnit rule fails the build if they ever do. Ownership is filtered **inside the query** (`findByIdAndOwnerId`), so "not yours" and "doesn't exist" are literally the same code path and both answer **404**, because a 403 would confirm the id exists.

**The module graph shaped the domain, not the other way round.** `catalog` may not depend on `pantry`, because that would close a cycle. So deleting an ingredient cannot ask "does this user still have stock of it?", and the answer isn't a workaround: the foreign key cascades the pantry row away, while an ingredient still used by a recipe line is refused outright. The rule that has real teeth got to decide the behaviour, and it is documented rather than papered over.

**The domain decides; SQL constraints are a safety net, except for uniqueness, where it's the other way round.** Deletion rules, stock non-negativity and state/timestamp coherence are enforced by services, with `CHECK` constraints and `RESTRICT` foreign keys behind them so that a bug becomes an error instead of corrupt data. Uniqueness (user email, ingredient name per owner) is the inverted case: two concurrent requests both pass the pre-check, so the unique index is what actually decides and the service translates the violation. The rule of thumb: if a constraint can fire with correct code, the database is in charge; if it can only fire because of a bug, the domain is.

## Stack

- **Java 25** (LTS) and **Spring Boot 4.1**: Spring MVC, Data JPA, Security, Validation, Actuator
- **PostgreSQL 18**, schema owned by **Flyway** (`ddl-auto: validate`, never `update`)
- **ArchUnit 1.4**: 18 architecture rules run as tests
- **Testcontainers 2**: integration and concurrency tests run against a real database; they fail without Docker rather than skipping
- **springdoc-openapi 3**: the generated contract is committed to `docs/openapi.json` and a test fails if it drifts
- **Checkstyle** for style. No auto-formatter on purpose: `google-java-format` and `palantir-java-format` reach into `javac` internals and break on JDK upgrades, and a lint gate that can break on a JDK bump is worse than none
- No Lombok, no MapStruct, no Mockito: no annotation processors, no bytecode agents, no generated sources

## Project structure

```
src/main/java/com/example/mealplan/
├── shared/       # UserId, units and quantities, error codes, ProblemDetail handling, Clock
├── iam/          # users, password hashing, JWT issuing and the security filter chain
├── catalog/      # ingredients and recipes
├── pantry/       # stock per ingredient
└── planning/     # plan entries and the cook operation
```

`catalog`, `pantry` and `planning` are split the same way (`api`, `domain`, `application`, `infrastructure`, `web`), and the dependency rule is one sentence: **a module only sees the `api` package of the others**, never their `domain`, `application`, `infrastructure` or `web`. The graph is `planning -> pantry -> catalog`, with `iam` standing alone and everything depending on `shared`. It is acyclic, and `ArchitectureTest` keeps it that way.

`iam` is the one module without an `api` package, on purpose: nothing depends on it. The only concept of its that crosses boundaries is the user identity, and that lives in `shared` so no module has to import an auth package just to name the owner of its own rows.

## Getting started

```bash
cp .env.example .env
docker compose up -d db
export MEALPLAN_JWT_SECRET=dev-only-secret-please-change-me-32
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`; interactive docs are at `http://localhost:8080/swagger-ui.html`. To run everything in containers instead: `docker compose up --build`.

Tests:

```bash
./mvnw verify        # unit, architecture, integration and concurrency; needs Docker running
```

There is no "fast" profile that skips the integration tests. A project whose point is behaviour under concurrency cannot have a default mode that never checks it. The only requirements are a JDK 25 and a running Docker daemon: no Maven install (there's a wrapper), no environment variables, no secrets.

## Status

Design settled, implementation in progress. When it lands, this section will say exactly what has been run and verified, not what is believed to work.

The following were left out **on purpose**, and each has a reason:

- **Un-cooking a plan entry.** Restoring stock would require knowing nobody touched it since; `COOKED` is a record of consumption and is terminal.
- **Recipe versioning and cloning.** A published recipe is immutable, so editing means archiving and creating another. `POST /api/recipes/{id}/clone` is the obvious next step.
- **Automatic retry on a locking conflict.** A 409 is returned instead, for the reason given above; wrapping `cook` in a retry loop is a small, deliberate follow-up rather than a default.
- **A shopping list.** It is derivable from plan minus pantry, and adds no new rule.
- **Sharing recipes between users, roles, and permissions.** Every user has exactly the same power over their own data.
- **Refresh tokens, logout and revocation.** The access token expires and you log in again.
- **Pagination.** Lists return the owner's full collection in a documented fixed order.
- **Nutrition data, nested recipes and ingredient substitutions.**
- **Rate limiting, caching, and metrics beyond `/actuator/health`.**
- **Publishing the container image.** CI builds it; pushing it would require credentials, and this workflow must pass in a fork with no secrets configured.

All data in this repository is fictitious: example domains, throwaway local database credentials and placeholder secrets only.

## License

MIT
