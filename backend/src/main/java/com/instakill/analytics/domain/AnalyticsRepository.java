package com.instakill.analytics.domain;

import java.util.List;
import java.util.UUID;

public interface AnalyticsRepository {

    List<WeeklyStats> weeklyStats(UUID userId);

    List<TopPost> topPostsByEngagement(UUID userId, int limit);

    /** Total likes and comments received over all of the user's posts: {likes, comments}. */
    long[] totalLikesAndComments(UUID userId);

    List<MediaTypeAverage> averageLikesByMediaType(UUID userId);

    EngagementRank engagementRank(UUID userId);
}
