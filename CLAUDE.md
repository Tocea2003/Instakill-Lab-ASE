# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Instakill is an Instagram-style feed: a Spring Boot 3.3 backend (Java 23, SQLite, JWT) plus a Vue 3 + Vite SPA. It's a monolith organized by bounded context so that contexts can later be extracted into services. The repo is used for a university software-engineering lab (ASE). Each assignment refactors a piece of the backend toward a design pattern, and each one gets its own `docs/taskN-*.md` write-up (see "Assignment workflow").

## Commands

The Gradle wrapper is gitignored. If `backend/gradlew` is missing, generate it with `./scripts/bootstrap-gradle.sh` (or `.ps1` on Windows), which downloads Gradle 8.9.

```bash
cd backend
./gradlew bootRun                     # run backend on :8080 (creates ./instakill.db, seeds alice/bob, password "password")
./gradlew test                        # all tests
./gradlew test --tests 'com.instakill.feed.application.FeedServiceTest'           # one class
./gradlew test --tests 'com.instakill.feed.application.FeedServiceTest.someTest'  # one method
./gradlew jacocoTestReport            # coverage -> build/reports/jacoco/test/html
./gradlew build                       # compile + test + jar
```

```bash
cd frontend
npm install
npm run dev      # :5173, proxies /api and /auth to :8080
npm run build
```

There is no linter or formatter configured. `backend/api.http` holds sample REST requests. Swagger UI is at `/api/swagger`.

To reset dev data, delete `backend/instakill.db`. `DataInitializer` only seeds when the users table is empty, and it doesn't run under the `test` profile.

## Architecture

Code lives in `backend/src/main/java/com/instakill/`, with one package per bounded context: `user`, `post`, `feed`, `interaction` (likes and comments). Each context follows a hexagonal layout:

- `domain/`: immutable Java `record`s that validate in their compact constructors (e.g. `Post` caps content at 1000 chars and requires non-negative counts), plus repository **interfaces** (`PostRepository`, etc.). Domain code has no JPA or Spring Data imports.
- `application/`: `@Service` classes annotated `@Transactional(readOnly = true)` at class level, with `@Transactional` on the methods that write.
- `api/rest/`: `@RestController`s and request/response DTOs under `/api/**`.

Cross-cutting packages:

- `infrastructure/persistence/`: JPA `*Entity` classes and Spring Data `Jpa*Repository` interfaces, plus `*JpaRepositoryAdapter` classes that implement the domain repository interfaces. The adapters convert between entity and domain types through `common/mapping/*Mapper`, which are static utilities that also build REST response DTOs.
- `infrastructure/config/`: `SecurityConfig`, `DataInitializer`, and the `SystemClock` / `UuidGenerator` implementations of `common/clock/Clock` and `common/id/IdGenerator`. Services take `Clock` and `IdGenerator` as constructor arguments instead of calling `Instant.now()` or `UUID.randomUUID()`, so unit tests mock them.
- `api/web/`: Thymeleaf MVC controllers and form objects for the server-rendered UI (`/feed`, `/posts/**`, `/auth/**`). Templates are in `src/main/resources/templates`.
- `common/error/`: `InstakillException` subclasses. `api/rest/RestExceptionHandler` maps them to RFC 7807 `ProblemDetail` responses (404, 403, 409, 422, and so on). This handler only applies to `@RestController`s.

### Cross-context coupling to know about

- `PostService.listFeed` delegates to `feed.application.FeedService`, which uses the Strategy pattern (Task 1). Spring injects every `FeedRankingStrategy` `@Component`, and `FeedService` indexes them by `sort()` into an `EnumMap`. To add a sort mode, add a `FeedSort` value (and map it in `FeedSort.from`) and write a new strategy class. Don't edit `FeedService`. Two strategies for the same sort fail at startup.
- `InteractionService` writes `Post.likeCount` and `Post.commentCount` through `PostRepository`. These counters are denormalized on the post, recomputed from `count*ByPostId` after every change. After adding a like it publishes a `LikeCreatedEvent` through `DomainEventPublisher` (Task 2). The only bean is `NoOpDomainEventPublisher`, a Null Object; to plug in a real publisher, mark it `@Primary`.

### Security

`SecurityConfig` defines two filter chains:

1. `/api/**`: stateless, CSRF disabled, returns 401/403 status codes. Public routes are `/api/auth/**`, Swagger, and `/api/health`.
2. Everything else (the web UI): CSRF is enabled, so MVC tests need `.with(csrf())`. The `/auth/logout` route clears the token cookie.

Both chains use `JwtAuthenticationFilter`, which reads the JWT **only from the `ACCESS_TOKEN` cookie**. `Authorization: Bearer` headers are not supported. The principal is a `JwtUserPrincipal`; get it with `@AuthenticationPrincipal`. JWT settings live under `instakill.jwt.*` in `application.yaml`.

### Tests

- Unit tests (`FeedServiceTest`, `*StrategyTest`, `InteractionServiceTest`) construct services directly with Mockito mocks and don't start Spring.
- `RestIntegrationTest` and `MvcFlowTest` use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`. The test profile uses `src/test/resources/application-test.yaml`, which points at an in-memory shared-cache SQLite DB with `create-drop`. They authenticate by copying the `ACCESS_TOKEN` cookie from the register/login response.

## Assignment workflow

Each refactoring task so far has followed the same shape, and new ones should too:

1. Behavior-preserving refactor toward a named pattern, with services depending on abstractions that Spring auto-wires.
2. Focused unit tests for each new abstraction and implementation, plus tests showing a new implementation plugs in without editing the consumer.
3. A `docs/taskN-<topic>.md` covering goal, before/after table, design, "how to extend", and files touched.
4. One commit per task on the `Assignment1_Refactoring` branch.
