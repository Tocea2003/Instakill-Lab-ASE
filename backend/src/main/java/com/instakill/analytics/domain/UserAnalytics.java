package com.instakill.analytics.domain;

import java.util.List;

/**
 * @param likeToCommentRatio total likes / total comments over all posts, null when there are no comments
 */
public record UserAnalytics(List<WeeklyStats> weekly,
                            List<TopPost> topPosts,
                            long totalLikes,
                            long totalComments,
                            Double likeToCommentRatio,
                            List<MediaTypeAverage> averageLikesByMediaType,
                            EngagementRank rank) {
}
