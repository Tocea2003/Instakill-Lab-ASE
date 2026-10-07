package com.instakill.feed.domain;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;

import java.util.List;

/**
 * Strategy interface for ranking the feed.
 *
 * Each implementation encapsulates ONE sorting algorithm and declares which
 * {@link FeedSort} mode it serves. FeedService collects every implementation
 * found in the Spring context, so supporting a new sort mode only requires a
 * new {@code @Component} implementing this interface (plus a new FeedSort value);
 * FeedService itself never has to change (Open/Closed Principle).
 */
public interface FeedRankingStrategy {

    /** The sort mode this strategy is responsible for. Must be unique across strategies. */
    FeedSort sort();

    /**
     * Fetches one page of the feed, ordered according to this strategy.
     *
     * @param repository source of posts
     * @param page       zero-based page index
     * @param size       page size
     */
    List<Post> fetch(PostRepository repository, int page, int size);
}
