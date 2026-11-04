package com.instakill.analytics.domain;

import java.time.LocalDate;

/** Posts created in one week (Monday start) plus the likes and comments those posts received. */
public record WeeklyStats(LocalDate weekStart, long posts, long likes, long comments) {
}
