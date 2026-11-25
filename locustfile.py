"""Locust load test for Instakill (Week 6).

    pip install locust
    locust -f locustfile.py --host=http://localhost:8080
    # headless, e.g. 10 users for 60 s:
    locust -f locustfile.py --host=http://localhost:8080 --headless -u 10 -r 10 -t 60s --html report.html

Every simulated user logs in once (JWT arrives in the ACCESS_TOKEN cookie, which Locust's
HTTP session keeps) and then repeats a weighted mix of what a real user does in the SPA:
read the feed, open comments, like, comment, post and look at their analytics.
The three seed accounts (alice, bob, charlie) all have the password "password".
"""
import random

from locust import HttpUser, between, task

ACCOUNTS = ["alice", "bob", "charlie"]
PASSWORD = "password"


class InstakillUser(HttpUser):
    wait_time = between(0.5, 2)

    def on_start(self):
        self.post_ids = []
        username = random.choice(ACCOUNTS)
        with self.client.post("/api/auth/login",
                              json={"usernameOrEmail": username, "password": PASSWORD},
                              name="POST /api/auth/login", catch_response=True) as resp:
            if resp.status_code != 200:
                resp.failure(f"login failed: {resp.status_code}")
        self._load_feed("new", 0)

    # --- helpers ---------------------------------------------------------------------------
    def _load_feed(self, sort, page):
        with self.client.get(f"/api/feed?sort={sort}&page={page}&size=10",
                             name=f"GET /api/feed?sort={sort}", catch_response=True) as resp:
            if resp.status_code != 200:
                resp.failure(f"feed {resp.status_code}")
                return
            ids = [p["id"] for p in resp.json()]
            if ids:
                self.post_ids = ids

    def _random_post(self):
        return random.choice(self.post_ids) if self.post_ids else None

    # --- tasks -----------------------------------------------------------------------------
    @task(5)
    def feed_newest(self):
        self._load_feed("new", random.randint(0, 20))

    @task(3)
    def feed_trending(self):
        self._load_feed("trending", random.randint(0, 5))

    @task(2)
    def view_comments(self):
        post_id = self._random_post()
        if post_id:
            self.client.get(f"/api/posts/{post_id}/comments?page=0&size=10",
                            name="GET /api/posts/{id}/comments")

    @task(2)
    def toggle_like(self):
        post_id = self._random_post()
        if post_id:
            self.client.post(f"/api/posts/{post_id}/likes/toggle", name="POST /api/posts/{id}/likes/toggle")

    @task(1)
    def add_comment(self):
        post_id = self._random_post()
        if post_id:
            self.client.post(f"/api/posts/{post_id}/comments",
                             json={"content": "Nice one! (locust)"},
                             name="POST /api/posts/{id}/comments")

    @task(1)
    def create_post(self):
        self.client.post("/api/posts", json={"content": "Load test post " + str(random.randint(1, 10**6))},
                         name="POST /api/posts")

    @task(1)
    def my_analytics(self):
        self.client.get("/api/analytics/me", name="GET /api/analytics/me")
