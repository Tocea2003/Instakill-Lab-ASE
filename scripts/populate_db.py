#!/usr/bin/env python3
"""Fill the Instakill SQLite database with configurable test data.

    pip install -r scripts/requirements.txt
    python scripts/populate_db.py                 # 10 000 users, 10-20 posts each
    python scripts/populate_db.py --users 500 --seed 1

What it does (all against the schema Hibernate created in backend/instakill.db):
  1. deletes likes, comments, posts and every user except `alice` and `bob`
  2. makes sure `charlie` exists (password: "password", bcrypt)
  3. generates users until the table holds --users rows in total, then for every user
     10-20 posts, each with 0-10 comments, 0-20 likes (distinct users), up to 10 random words,
     a random photo/video URL and a created_on within the last 7 days.
  4. checks integrity (no orphans, like_count/comment_count match, no duplicate likes)

Stop the backend first so SQLite is not locked. The backend's tables must already exist
(start it once; Hibernate `ddl-auto: update` creates them).
Timestamps are epoch milliseconds, ids are UUID strings: that is how the Spring app stores them.
"""
import argparse
import random
import sqlite3
import sys
import time
import uuid
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

try:
    import bcrypt
    from faker import Faker
except ImportError:
    sys.exit("Missing dependencies: pip install -r scripts/requirements.txt")

DEFAULT_DB = Path(__file__).resolve().parent.parent / "backend" / "instakill.db"
KEEP_USERS = ("alice", "bob")
DEFAULT_PASSWORD = "password"

# Remote sample media so the feed renders; the file extension is what marks a post as photo or video.
PHOTO_URL = "https://picsum.photos/seed/instakill-{n}/600/400.jpg"
VIDEO_URLS = [
    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
    "https://www.w3schools.com/html/mov_bbb.mp4",
]


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--db", type=Path, default=DEFAULT_DB, help="SQLite file (default: backend/instakill.db)")
    p.add_argument("--users", type=int, default=10_000,
                   help="total number of users after the run, including alice, bob and charlie (default: 10000)")
    p.add_argument("--min-posts", type=int, default=10)
    p.add_argument("--max-posts", type=int, default=20)
    p.add_argument("--max-comments", type=int, default=10, help="comments per post: 0..N (default: 10)")
    p.add_argument("--max-likes", type=int, default=20, help="likes per post: 0..N (default: 20)")
    p.add_argument("--max-words", type=int, default=10, help="words per post: 1..N (default: 10)")
    p.add_argument("--days", type=int, default=7, help="posts are created within the last N days (default: 7)")
    p.add_argument("--batch-size", type=int, default=20_000, help="rows buffered before each commit")
    p.add_argument("--seed", type=int, help="seed for reproducible data")
    return p.parse_args()


def ms(dt: datetime) -> int:
    return int(dt.timestamp() * 1000)


def new_id() -> str:
    return str(uuid.uuid4())


class Populator:
    def __init__(self, conn, args):
        self.conn = conn
        self.args = args
        self.fake = Faker()
        self.now = datetime.now(timezone.utc)
        self.used_names = set()
        self.buf = {"posts": [], "comments": [], "likes": []}
        self.counts = {"users": 0, "posts": 0, "comments": 0, "likes": 0}

    # --- transactions -------------------------------------------------------------------------
    def run_tx(self, fn):
        self.conn.execute("BEGIN")
        try:
            fn()
        except BaseException:
            self.conn.execute("ROLLBACK")
            raise
        self.conn.execute("COMMIT")

    # --- users --------------------------------------------------------------------------------
    def unique_username(self) -> str:
        base = self.fake.user_name()[:30]
        name = base
        while name.lower() in self.used_names:
            name = f"{base}{random.randint(1, 999_999)}"
        self.used_names.add(name.lower())
        return name

    def create_users(self):
        """Returns the list of all user ids (kept users + charlie + generated)."""
        kept = self.conn.execute(
            f"SELECT id, username FROM users WHERE username IN ({','.join('?' * len(KEEP_USERS))})",
            KEEP_USERS).fetchall()
        user_ids = [row[0] for row in kept]
        self.used_names.update(row[1].lower() for row in kept)
        self.used_names.add("charlie")

        # One bcrypt hash is reused for every generated user: hashing 10 000 passwords would take minutes.
        password_hash = bcrypt.hashpw(DEFAULT_PASSWORD.encode(), bcrypt.gensalt(rounds=10)).decode()
        rows = [(new_id(), ms(self.now), "charlie@example.com", password_hash, "charlie")]

        total_generated = max(0, self.args.users - len(kept) - 1)
        for _ in range(total_generated):
            name = self.unique_username()
            created = self.now - timedelta(days=random.randint(8, 365))
            rows.append((new_id(), ms(created), f"{name.lower()}@example.com", password_hash, name))

        def insert():
            for i in range(0, len(rows), self.args.batch_size):
                self.conn.executemany(
                    "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (?,?,?,?,?)",
                    rows[i:i + self.args.batch_size])

        self.run_tx(insert)
        self.counts["users"] = len(rows)
        user_ids.extend(r[0] for r in rows)
        return user_ids

    # --- posts / comments / likes -------------------------------------------------------------
    def random_created_on(self) -> datetime:
        """Day from faker.date_between_dates (last N days), random time of day, never in the future."""
        today = self.now.date()
        day = self.fake.date_between_dates(date_start=today - timedelta(days=self.args.days), date_end=today)
        start = datetime.combine(day, datetime.min.time(), tzinfo=timezone.utc)
        seconds_available = 86_399 if day < today else int((self.now - start).total_seconds())
        return start + timedelta(seconds=random.randint(0, max(0, seconds_available)))

    def random_media_url(self) -> str:
        if random.random() < 0.5:
            return PHOTO_URL.format(n=random.randint(1, 1000))
        return random.choice(VIDEO_URLS)

    def add_post(self, author_id, user_ids):
        a = self.args
        post_id = new_id()
        created = self.random_created_on()
        created_ms = ms(created)
        n_comments = random.randint(0, a.max_comments)
        n_likes = random.randint(0, min(a.max_likes, len(user_ids)))
        content = " ".join(self.fake.words(nb=random.randint(1, a.max_words)))

        self.buf["posts"].append((post_id, author_id, n_comments, content, created_ms,
                                  self.random_media_url(), n_likes, created_ms))
        span = max(1, int((self.now - created).total_seconds()))
        for _ in range(n_comments):
            at = created + timedelta(seconds=random.randint(0, span))
            self.buf["comments"].append((new_id(), random.choice(user_ids),
                                         self.fake.sentence(nb_words=random.randint(3, 12))[:500], ms(at), post_id))
        for liker in random.sample(user_ids, n_likes):  # distinct users: unique (post, user)
            at = created + timedelta(seconds=random.randint(0, span))
            self.buf["likes"].append((new_id(), ms(at), post_id, liker))

    def flush(self):
        buf = self.buf

        def insert():
            self.conn.executemany(
                "INSERT INTO posts (id, author_id, comment_count, content, created_at, image_url, like_count, updated_at)"
                " VALUES (?,?,?,?,?,?,?,?)", buf["posts"])
            self.conn.executemany(
                "INSERT INTO comments (id, author_id, content, created_at, post_id) VALUES (?,?,?,?,?)", buf["comments"])
            self.conn.executemany(
                "INSERT INTO likes (id, created_at, post_id, user_id) VALUES (?,?,?,?)", buf["likes"])

        self.run_tx(insert)
        for table in ("posts", "comments", "likes"):
            self.counts[table] += len(buf[table])
            buf[table].clear()

    def create_content(self, user_ids):
        a = self.args
        started = time.time()
        for i, author in enumerate(user_ids, 1):
            for _ in range(random.randint(a.min_posts, a.max_posts)):
                self.add_post(author, user_ids)
            # a post and its comments/likes always go into the same commit, so no batch ever leaves orphans
            if sum(len(v) for v in self.buf.values()) >= a.batch_size:
                self.flush()
            if i % 1000 == 0:
                print(f"  {i}/{len(user_ids)} users done ({time.time() - started:.0f}s)")
        self.flush()


def check_integrity(conn) -> bool:
    checks = {
        "posts without author": "SELECT COUNT(*) FROM posts WHERE author_id NOT IN (SELECT id FROM users)",
        "comments without post": "SELECT COUNT(*) FROM comments WHERE post_id NOT IN (SELECT id FROM posts)",
        "comments without author": "SELECT COUNT(*) FROM comments WHERE author_id NOT IN (SELECT id FROM users)",
        "likes without post": "SELECT COUNT(*) FROM likes WHERE post_id NOT IN (SELECT id FROM posts)",
        "likes without user": "SELECT COUNT(*) FROM likes WHERE user_id NOT IN (SELECT id FROM users)",
        "duplicate likes": "SELECT COUNT(*) FROM (SELECT 1 FROM likes GROUP BY post_id, user_id HAVING COUNT(*) > 1)",
        "wrong like_count": "SELECT COUNT(*) FROM posts p WHERE p.like_count <> (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id)",
        "wrong comment_count": "SELECT COUNT(*) FROM posts p WHERE p.comment_count <> (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id)",
    }
    ok = True
    for label, sql in checks.items():
        n = conn.execute(sql).fetchone()[0]
        print(f"  {'ok ' if n == 0 else 'FAIL'} {label}: {n}")
        ok &= n == 0
    return ok


def main():
    args = parse_args()
    if args.min_posts > args.max_posts or args.users < 1:
        sys.exit("Invalid arguments: need --min-posts <= --max-posts and --users >= 1")
    if args.seed is not None:
        random.seed(args.seed)
        Faker.seed(args.seed)
    if not args.db.exists():
        sys.exit(f"{args.db} not found: start the backend once so it creates the database, or pass --db")

    conn = sqlite3.connect(args.db, isolation_level=None, timeout=30)  # explicit transactions
    tables = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    if not {"users", "posts", "comments", "likes"} <= tables:
        sys.exit("Tables missing: start the backend once so Hibernate creates the schema.")
    conn.execute("PRAGMA synchronous = OFF")

    started = time.time()
    pop = Populator(conn, args)

    print("Deleting likes, comments, posts and users (except alice, bob)...")

    def wipe():
        conn.execute("DELETE FROM likes")
        conn.execute("DELETE FROM comments")
        conn.execute("DELETE FROM posts")
        conn.execute("DELETE FROM users WHERE username NOT IN (?, ?)", KEEP_USERS)

    pop.run_tx(wipe)

    print("Creating users...")
    user_ids = pop.create_users()
    print(f"Creating posts, comments and likes for {len(user_ids)} users...")
    pop.create_content(user_ids)

    # Hibernate does not always create this index on an existing database; without it every per-post
    # like count (here and in the analytics queries) scans the whole likes table.
    print("Ensuring indexes...")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_likes_post ON likes (post_id)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_comments_post ON comments (post_id)")

    print("Integrity check:")
    ok = check_integrity(conn)
    conn.close()
    print(f"Done in {time.time() - started:.1f}s: " + ", ".join(f"{v} {k}" for k, v in pop.counts.items()))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
