package com.instakill.analytics.application;

import com.instakill.analytics.domain.AnalyticsRepository;
import com.instakill.analytics.domain.UserAnalytics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    static final int TOP_POSTS = 5;

    private final AnalyticsRepository repository;

    public AnalyticsService(AnalyticsRepository repository) {
        this.repository = repository;
    }

    public UserAnalytics getForUser(UUID userId) {
        long[] totals = repository.totalLikesAndComments(userId);
        Double ratio = totals[1] == 0 ? null : (double) totals[0] / totals[1];
        return new UserAnalytics(
                repository.weeklyStats(userId),
                repository.topPostsByEngagement(userId, TOP_POSTS),
                totals[0],
                totals[1],
                ratio,
                repository.averageLikesByMediaType(userId),
                repository.engagementRank(userId));
    }
}
