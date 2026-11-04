package com.instakill.infrastructure.persistence;

import com.instakill.analytics.domain.AnalyticsRepository;
import com.instakill.analytics.domain.EngagementRank;
import com.instakill.analytics.domain.MediaTypeAverage;
import com.instakill.analytics.domain.TopPost;
import com.instakill.analytics.domain.WeeklyStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * All statistics are computed by SQLite. Timestamps are stored as epoch milliseconds and ids as CHAR(36),
 * so the user id is passed as a string. Likes/comments are counted from their tables (source of truth),
 * except for the cross-user ranking, which uses the denormalized counters on posts to stay fast on large data.
 */
@Repository
public class AnalyticsJdbcRepositoryAdapter implements AnalyticsRepository {

    /** Per-post likes and comments for one author. Placeholder: author id. */
    private static final String USER_POSTS = """
            WITH pe AS (
              SELECT p.id, p.content, p.image_url, p.created_at,
                     (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS likes,
                     (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS comments
              FROM posts p WHERE p.author_id = ?
            )
            """;

    private final JdbcTemplate jdbc;

    public AnalyticsJdbcRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WeeklyStats> weeklyStats(UUID userId) {
        // 'weekday 0' moves to the coming Sunday, -6 days gives the Monday that starts the week.
        return jdbc.query(USER_POSTS + """
                        SELECT date(created_at / 1000, 'unixepoch', 'weekday 0', '-6 days') AS week_start,
                               COUNT(*) AS posts, SUM(likes) AS likes, SUM(comments) AS comments
                        FROM pe GROUP BY week_start ORDER BY week_start
                        """,
                (rs, i) -> new WeeklyStats(LocalDate.parse(rs.getString("week_start")),
                        rs.getLong("posts"), rs.getLong("likes"), rs.getLong("comments")),
                userId.toString());
    }

    @Override
    public List<TopPost> topPostsByEngagement(UUID userId, int limit) {
        return jdbc.query(USER_POSTS + """
                        SELECT id, content, created_at, likes, comments, likes + comments AS engagement
                        FROM pe ORDER BY engagement DESC, created_at DESC LIMIT ?
                        """,
                (rs, i) -> new TopPost(UUID.fromString(rs.getString("id")), rs.getString("content"),
                        Instant.ofEpochMilli(rs.getLong("created_at")),
                        rs.getLong("likes"), rs.getLong("comments"), rs.getLong("engagement")),
                userId.toString(), limit);
    }

    @Override
    public long[] totalLikesAndComments(UUID userId) {
        return jdbc.queryForObject(USER_POSTS + """
                        SELECT COALESCE(SUM(likes), 0) AS likes, COALESCE(SUM(comments), 0) AS comments FROM pe
                        """,
                (rs, i) -> new long[]{rs.getLong("likes"), rs.getLong("comments")},
                userId.toString());
    }

    @Override
    public List<MediaTypeAverage> averageLikesByMediaType(UUID userId) {
        // Posts without media are left out: the comparison is photo vs video.
        return jdbc.query(USER_POSTS + """
                        SELECT CASE WHEN lower(image_url) LIKE '%.mp4' OR lower(image_url) LIKE '%.mov'
                                      OR lower(image_url) LIKE '%.webm' THEN 'video' ELSE 'photo' END AS media_type,
                               COUNT(*) AS posts, AVG(likes) AS avg_likes
                        FROM pe WHERE image_url IS NOT NULL AND image_url <> ''
                        GROUP BY media_type ORDER BY media_type
                        """,
                (rs, i) -> new MediaTypeAverage(rs.getString("media_type"), rs.getLong("posts"), rs.getDouble("avg_likes")),
                userId.toString());
    }

    @Override
    public EngagementRank engagementRank(UUID userId) {
        String id = userId.toString();
        return jdbc.queryForObject("""
                        WITH author_avg AS (
                          SELECT author_id, AVG(like_count + comment_count) AS avg_eng
                          FROM posts GROUP BY author_id
                        )
                        SELECT (SELECT avg_eng FROM author_avg WHERE author_id = ?) AS mine,
                               (SELECT COUNT(*) FROM author_avg) AS total,
                               (SELECT COUNT(*) FROM author_avg
                                 WHERE avg_eng > (SELECT avg_eng FROM author_avg WHERE author_id = ?)) AS higher
                        """,
                (rs, i) -> {
                    double mine = rs.getDouble("mine");
                    boolean hasPosts = !rs.wasNull();
                    long total = rs.getLong("total");
                    long higher = rs.getLong("higher");
                    Integer topPercent = hasPosts ? (int) Math.ceil(100.0 * (higher + 1) / total) : null;
                    return new EngagementRank(mine, total, topPercent);
                }, id, id);
    }
}
