package com.instakill.analytics.domain;

/** Average likes per post for one media type ("photo" or "video"). */
public record MediaTypeAverage(String mediaType, long posts, double averageLikes) {
}
