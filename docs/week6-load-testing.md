# Week 6: Load testing Instakill with Locust (SQLite vs PostgreSQL)

## Goal

Measure how the backend behaves under concurrent users with the default SQLite database, then repeat the
same scenarios on PostgreSQL and compare failures, latency and resource use.

## Setup

| Item | Value |
|---|---|
| Machine | one Apple-silicon Mac; Locust, the backend JVM and PostgreSQL 17 (Homebrew) all run on it |
| Data | 10 000 users, ~150 000 posts, ~750 000 comments, ~1 500 000 likes in **both** databases (`scripts/populate_db.py`) |
| Scenario | `locustfile.py`: each simulated user logs in once, then waits 0.5-2 s between tasks. Weights: feed newest 5, feed trending 3, view comments 2, toggle like 2, add comment 1, create post 1, `/api/analytics/me` 1 |
| Runs | 1, 2, 5, 10, 20, 50 users, 60 s each, spawn rate = number of users |
| Resources | CPU and RSS of the backend JVM sampled every 2 s (`ps`) |
| Raw results | `loadtest-results/<db>/`: Locust CSVs, `report-N.html` (Statistics and Charts pages), `resources-N.csv` |

Reproduce:

```bash
pip install locust "psycopg[binary]" faker bcrypt
# SQLite (default profile)
cd backend && ./gradlew bootRun
scripts/run_loadtest.sh sqlite 60s 1 2 5 10 20 50
# PostgreSQL
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun      # once, so Hibernate creates the schema
python scripts/populate_db.py --postgres --seed 1       # stop the backend first
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
scripts/run_loadtest.sh postgres 60s 1 2 5 10 20 50
```

Interactive run as in the assignment: `locust -f locustfile.py --host=http://localhost:8080`, then open
http://localhost:8089.

## Results

Response times in ms, aggregated over all endpoints. CPU is the backend JVM's average (100 % = one core).

### SQLite

| Users | Requests | Fails | Avg | p50 | p95 | p99 | Max | Req/s | CPU | RSS |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 48 | 0 | 22.9 | 19 | 58 | 110 | 112 | 0.8 | 1.5 % | 548 MB |
| 2 | 95 | 0 | 19.6 | 14 | 60 | 99 | 99 | 1.6 | 2.3 % | 553 MB |
| 5 | 251 | 0 | 13.7 | 10 | 55 | 95 | 104 | 4.3 | 4.8 % | 566 MB |
| 10 | 486 | **1** (0.2 %) | 19.1 | 11 | 58 | 170 | 385 | 8.2 | 9.6 % | 523 MB |
| 20 | 996 | **8** (0.8 %) | 15.1 | 8 | 56 | 170 | 197 | 16.9 | 11.9 % | 528 MB |
| 50 | 2444 | **31** (1.3 %) | 22.8 | 6 | 81 | 370 | 743 | 41.2 | 23.5 % | 590 MB |

### PostgreSQL

| Users | Requests | Fails | Avg | p50 | p95 | p99 | Max | Req/s | CPU | RSS |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 47 | 0 | 14.2 | 11 | 61 | 68 | 68 | 0.8 | 0.2 % | 281 MB |
| 2 | 98 | 0 | 16.2 | 12 | 68 | 73 | 73 | 1.7 | 1.1 % | 280 MB |
| 5 | 246 | 0 | 16.7 | 11 | 58 | 100 | 160 | 4.2 | 1.9 % | 301 MB |
| 10 | 493 | 0 | 14.7 | 11 | 58 | 92 | 101 | 8.3 | 4.3 % | 301 MB |
| 20 | 1004 | 0 | 16.0 | 10 | 59 | 120 | 190 | 17.0 | 7.5 % | 301 MB |
| 50 | 2450 | 0 | 19.7 | 8 | 58 | 370 | 520 | 41.5 | 14.5 % | 303 MB |

### Per endpoint at 50 users (avg ms, p95 ms)

| Endpoint | SQLite | PostgreSQL |
|---|---|---|
| `GET /api/feed?sort=new` | 24 / 170 | 21 / 51 |
| `GET /api/feed?sort=trending` | 9 / 25 | 10 / 14 |
| `GET /api/posts/{id}/comments` | 5 / 10 | 5 / 8 |
| `GET /api/analytics/me` | 45 / 60 | 49 / 63 |
| `POST /api/auth/login` | 295 / 730 | 281 / 480 |
| `POST /api/posts` | 11 / 52 (5 fails) | 7 / 10 |
| `POST /api/posts/{id}/comments` | 9 / 35 (8 fails) | 7 / 12 |
| `POST /api/posts/{id}/likes/toggle` | 12 / 52 (18 fails) | 7 / 11 |

## Observations

**SQLite**
- 1 to 5 users: no failures. Average latency is 14-23 ms and does not rise with load (the first requests, which
  include the bcrypt login and cold caches, make the single-user average the highest).
- The first failures appear at **10 users** and grow with load: 1 at 10 users, 8 at 20, 31 at 50 (1.3 %).
- Every failure is an HTTP 500 on a **write** (like toggle, comment, new post). The backend log shows
  `[SQLITE_BUSY] The database file is locked`. No read ever failed.
- Averages stay low even at 50 users, but the tail degrades: p99 370 ms, max 743 ms, and p95 for the newest feed
  170 ms.

**PostgreSQL**
- 0 failures in all 6 runs (4 338 requests in total, against 4 320 requests with 40 failures on SQLite).
- Average latency is about the same as SQLite (14-20 ms), so at this load PostgreSQL is not faster on average.
  The difference is in the tail of the reads and in the writes: p95 of the newest feed at 50 users drops from
  170 ms to 51 ms and the write endpoints are faster.
- Throughput is the same (41 req/s at 50 users), because it is bounded by the users' think time (0.5-2 s), not by
  the server. The scenario never saturates either database.

**Resources**
- JVM RSS is ~520-590 MB on SQLite and ~280-300 MB on PostgreSQL. SQLite runs inside the JVM process (its page
  cache and file mapping count towards RSS); PostgreSQL's memory lives in its own processes.
- JVM CPU is lower on PostgreSQL (14.5 % vs 23.5 % at 50 users), but the comparison is not like for like: the
  database work moved to separate PostgreSQL processes whose CPU was **not** measured.

## Why

- **Single writer.** SQLite allows one writer at a time with a database-wide lock. With a connection pool, several
  request threads try to write at once; the ones that cannot get the lock fail with `SQLITE_BUSY`. A like toggle
  reads, then writes, and updates the denormalized counters on the post: this read-then-write sequence in one
  transaction is the usual way to get `SQLITE_BUSY` immediately, without waiting for the busy timeout (likely
  cause; not verified in isolation). PostgreSQL uses row-level locks and MVCC, so writers to different rows do not
  block each other and readers never block writers.
- **Tail latency of reads.** On SQLite a write holding the lock can stall concurrent work, which shows up in p99
  and max rather than in the average. PostgreSQL's planner and shared buffers keep the newest-feed query stable.
- **Login dominates the average.** `POST /api/auth/login` costs ~280-300 ms on both databases because of bcrypt;
  it is database independent.
- **Load is light.** At 50 users the server only sees ~41 req/s. Differences would grow with more users or a shorter
  wait time.

## Caveats

- Locust, the backend and the database share one laptop, and each point is a single 60-second run, so small
  differences (a few ms in the average) are noise. The failure counts did reproduce: SQLite was run twice at 10 and
  20 users (2 and 1 fails at 10 users, 10 and 8 fails at 20).
- The machine went to sleep during part of the first Postgres runs, which truncated them. `run_loadtest.sh` now
  detects such runs (too few CPU samples or a wall time far above the duration) and repeats them; the numbers above
  are all from complete runs.
- SQLite and PostgreSQL hold the same volume of data but not the same rows (the SQLite data was generated without
  a seed). Both received the load test's own writes.
- PostgreSQL ran with default settings (`shared_buffers` 128 MB, no tuning).

## Code changes this needed

| File | Change |
|---|---|
| `backend/build.gradle.kts` | `runtimeOnly("org.postgresql:postgresql")` |
| `backend/src/main/resources/application-postgres.yaml` | new `postgres` profile (URL, user, password overridable with `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`) |
| `AnalyticsJdbcRepositoryAdapter` | the analytics SQL was SQLite specific (`unixepoch`, `weekday`, epoch-millis `created_at`). It now detects the database and uses `date_trunc('week', ...)` and `timestamptz` on PostgreSQL. SQLite behaviour is unchanged |
| `scripts/populate_db.py` | `--postgres [DSN]` option (psycopg, native timestamps, `ANALYZE` afterwards). Integrity checks use `NOT EXISTS`, because `NOT IN (SELECT ...)` over 150 000 ids did not finish on PostgreSQL |
| `scripts/requirements.txt` | adds `psycopg[binary]` |
| `locustfile.py`, `scripts/run_loadtest.sh` | the scenario and the runner that also samples CPU/memory |

## Screenshots for the submission

The Locust Statistics and Charts pages are in `loadtest-results/sqlite/report-50.html` and
`loadtest-results/postgres/report-50.html` (and the other user counts). Open them in a browser and take the
screenshots from there, or run the interactive Locust UI at http://localhost:8089.
