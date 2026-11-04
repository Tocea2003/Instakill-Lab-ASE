package com.instakill.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanContent() {
        // the in-memory DB is shared with the other integration tests; the ranking needs a known set of authors
        jdbc.update("DELETE FROM likes");
        jdbc.update("DELETE FROM comments");
        jdbc.update("DELETE FROM posts");
    }

    private Cookie register(String name) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"email\":\"" + name + "@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private String userId(String name) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", String.class, name);
    }

    private String insertPost(String authorId, String imageUrl, String isoInstant, int likes, int comments) {
        String id = UUID.randomUUID().toString();
        long ts = Instant.parse(isoInstant).toEpochMilli();
        jdbc.update("INSERT INTO posts (id, author_id, content, image_url, created_at, updated_at, like_count, comment_count) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, authorId, "post " + id.substring(0, 4), imageUrl, ts, ts, likes, comments);
        for (int i = 0; i < likes; i++) {
            jdbc.update("INSERT INTO likes (id, post_id, user_id, created_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID().toString(), id, UUID.randomUUID().toString(), ts);
        }
        for (int i = 0; i < comments; i++) {
            jdbc.update("INSERT INTO comments (id, post_id, author_id, content, created_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), id, UUID.randomUUID().toString(), "c", ts);
        }
        return id;
    }

    @Test
    void computesAllStatisticsInSql() throws Exception {
        Cookie token = register("analytics_a");
        register("analytics_b");
        register("analytics_c");
        String a = userId("analytics_a");

        String photo = insertPost(a, "/media/a.PNG", "2024-01-01T10:00:00Z", 3, 1);   // Monday, week 2024-01-01
        insertPost(a, "/media/b.mp4", "2024-01-03T10:00:00Z", 1, 0);                   // same week
        String text = insertPost(a, null, "2024-01-10T10:00:00Z", 0, 2);               // week 2024-01-08
        insertPost(userId("analytics_b"), null, "2024-01-02T10:00:00Z", 0, 0);         // avg engagement 0
        insertPost(userId("analytics_c"), null, "2024-01-02T10:00:00Z", 10, 0);        // avg engagement 10

        JsonNode json = objectMapper.readTree(mockMvc.perform(get("/api/analytics/me").cookie(token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode weekly = json.get("weekly");
        assertThat(weekly).hasSize(2);
        assertThat(weekly.get(0).get("weekStart").asText()).isEqualTo("2024-01-01");
        assertThat(weekly.get(0).get("posts").asLong()).isEqualTo(2);
        assertThat(weekly.get(0).get("likes").asLong()).isEqualTo(4);
        assertThat(weekly.get(0).get("comments").asLong()).isEqualTo(1);
        assertThat(weekly.get(1).get("weekStart").asText()).isEqualTo("2024-01-08");
        assertThat(weekly.get(1).get("comments").asLong()).isEqualTo(2);

        JsonNode top = json.get("topPosts");
        assertThat(top).hasSize(3);
        assertThat(top.get(0).get("postId").asText()).isEqualTo(photo);
        assertThat(top.get(0).get("engagement").asLong()).isEqualTo(4);
        assertThat(top.get(1).get("postId").asText()).isEqualTo(text);

        assertThat(json.get("totalLikes").asLong()).isEqualTo(4);
        assertThat(json.get("totalComments").asLong()).isEqualTo(3);
        assertThat(json.get("likeToCommentRatio").asDouble()).isEqualTo(4.0 / 3, org.assertj.core.data.Offset.offset(1e-9));

        JsonNode media = json.get("averageLikesByMediaType");
        assertThat(media).hasSize(2);
        assertThat(media.get(0).get("mediaType").asText()).isEqualTo("photo");
        assertThat(media.get(0).get("averageLikes").asDouble()).isEqualTo(3.0);
        assertThat(media.get(1).get("mediaType").asText()).isEqualTo("video");
        assertThat(media.get(1).get("averageLikes").asDouble()).isEqualTo(1.0);

        JsonNode rank = json.get("rank");
        assertThat(rank.get("averageEngagement").asDouble()).isEqualTo(7.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(rank.get("totalAuthors").asLong()).isEqualTo(3);
        assertThat(rank.get("topPercent").asInt()).isEqualTo(67); // second of three authors
    }

    @Test
    void userWithoutPostsGetsEmptyStatistics() throws Exception {
        Cookie token = register("analytics_empty");

        JsonNode json = objectMapper.readTree(mockMvc.perform(get("/api/analytics/me").cookie(token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(json.get("weekly")).isEmpty();
        assertThat(json.get("topPosts")).isEmpty();
        assertThat(json.get("likeToCommentRatio").isNull()).isTrue();
        assertThat(json.get("rank").get("topPercent").isNull()).isTrue();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/me")).andExpect(status().isUnauthorized());
    }
}
