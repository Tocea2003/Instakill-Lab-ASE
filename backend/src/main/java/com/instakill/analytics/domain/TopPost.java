package com.instakill.analytics.domain;

import java.time.Instant;
import java.util.UUID;

public record TopPost(UUID postId, String content, Instant createdAt, long likes, long comments, long engagement) {
}
