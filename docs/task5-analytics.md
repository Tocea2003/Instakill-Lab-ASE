# Task 5 – Analytics view and test-data script

## Goal
Show the logged-in user a small analytics view (backend + frontend) computed with SQL, and provide a Python
script that fills `instakill.db` with configurable test data so the numbers are meaningful.

## Before / after

| Before | After |
|--------|-------|
| No statistics | `GET /api/analytics/me` returns weekly totals, top 5 posts, like:comment ratio, photo-vs-video average likes and the user's engagement rank |
| Feed only in the SPA | "Feed / Analytics" buttons in the header; `Analytics.vue` renders the data |
| Only seed users | `scripts/populate_db.py` creates 10 000 users with posts, comments and likes in ~30 s |

## Design
- New bounded context `analytics` (read only): `domain` (records + `AnalyticsRepository`), `application.AnalyticsService`
  (`@Transactional(readOnly = true)`), `api.rest.AnalyticsRestController` (under `/api/**`, so 401 when anonymous).
- `infrastructure.persistence.AnalyticsJdbcRepositoryAdapter` runs plain SQL through `JdbcTemplate` (SQLite does all aggregation).
  Timestamps are epoch milliseconds, ids are `CHAR(36)`.
  1. **Per week**: Monday of the post's week, `date(created_at/1000,'unixepoch','weekday 0','-6 days')`; posts, likes, comments per group.
  2. **Top 5**: `likes + comments` per post, `ORDER BY engagement DESC, created_at DESC LIMIT 5`.
  3. **Ratio**: total likes / total comments, `null` when there are no comments.
  4. **Photo vs video**: there is no media-type column, so the type comes from the `image_url` extension
     (`.mp4 .mov .webm` = video, anything else = photo); posts without media are excluded.
  5. **Rank**: average `like_count + comment_count` per author over all authors with posts;
     `topPercent = ceil(100 * (authors with a higher average + 1) / authors)`.
- Assumptions: likes/comments of a post count for the week the **post** was created; the rank uses the denormalized
  counters on `posts` (kept in sync by `InteractionService`) so it stays fast across all users, the per-user figures count
  rows in `likes`/`comments`; "top X %" is the computed value, not an input.
- `LikeEntity` gets `idx_likes_post (post_id)`; Hibernate creates it on startup (the populate script also creates it if missing).
  Without it every per-post like count scans the whole `likes` table.

## populate_db.py
```bash
pip install -r scripts/requirements.txt          # faker, bcrypt
python scripts/populate_db.py                    # defaults: 10 000 users, 10-20 posts, 0-10 comments, 0-20 likes
python scripts/populate_db.py --users 500 --seed 1 --db backend/instakill.db
```
Stop the backend first and start it once beforehand if the database does not exist yet (Hibernate creates the schema).
Options: `--users` (total after the run, including alice, bob, charlie), `--min-posts`, `--max-posts`, `--max-comments`,
`--max-likes`, `--max-words`, `--days`, `--batch-size`, `--seed`.

- Deletes likes, comments, posts, and all users except `alice` and `bob`; creates `charlie` / `password` (bcrypt `$2b$`, accepted by Spring's encoder).
  Generated users share one precomputed hash of `password` (hashing 10 000 passwords would take minutes).
- Usernames are unique (faker + suffix on collision); `created_on` days come from `faker.date_between_dates()` over the last 7 days, plus a random time that is never in the future.
- Photo/video URLs are remote samples (`picsum.photos` `.jpg`, two public `.mp4`).
- A post and its comments/likes are inserted in the same transaction, committed in batches (`executeMany`), so no batch can leave orphans.
  Likes use `random.sample` of distinct users; `like_count`/`comment_count` equal the inserted rows.
- Ends with an integrity check (orphans, duplicate likes, counter mismatches) and exits non-zero if it fails.

## How to extend
Add a statistic: a method on `AnalyticsRepository`, its SQL in the adapter, a field on `UserAnalytics`, a block in `Analytics.vue`.
If a real `media_type` column is added later, replace the `CASE` on `image_url`.

## Files
- New: `analytics/**` (domain, application, api), `infrastructure/persistence/AnalyticsJdbcRepositoryAdapter.java`,
  `frontend/src/Analytics.vue`, `scripts/populate_db.py`, `scripts/requirements.txt`
- Changed: `LikeEntity.java` (index), `frontend/src/{App.vue,api.js}`
- Tests: `AnalyticsIntegrationTest` (known data set: weekly, top posts, ratio, media averages, rank, empty user, 401)

## Verification done
Full backend suite green; populate on a copy of the DB (10 000 users: 149 806 posts, 749 466 comments, 1 496 996 likes, all
integrity checks ok, 33 s); analytics endpoint for `charlie` answers in ~0.3 s and matches hand-run `sqlite3` queries.
