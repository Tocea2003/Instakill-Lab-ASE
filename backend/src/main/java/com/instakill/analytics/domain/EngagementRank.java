package com.instakill.analytics.domain;

/**
 * Where the user sits among all authors by average engagement (likes + comments per post).
 *
 * @param averageEngagement the user's own average, 0 when the user has no posts
 * @param totalAuthors      number of users with at least one post
 * @param topPercent        "top X %": 1 is the best; null when the user has no posts
 */
public record EngagementRank(double averageEngagement, long totalAuthors, Integer topPercent) {
}
