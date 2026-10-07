package com.instakill.feed.application;

import com.instakill.feed.domain.FeedRankingStrategy;
import com.instakill.feed.domain.FeedSort;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the feed by delegating to a {@link FeedRankingStrategy}.
 *
 * This class contains no sorting logic and no switch on the sort mode: Spring injects
 * every strategy bean, and we index them by the {@link FeedSort} they declare.
 * Adding a new sort mode means adding a new strategy class, not editing this one.
 */
@Service
public class FeedService {

    private final PostRepository posts;
    private final Map<FeedSort, FeedRankingStrategy> strategies = new EnumMap<>(FeedSort.class);

    public FeedService(PostRepository posts, List<FeedRankingStrategy> rankingStrategies) {
        this.posts = posts;
        for (FeedRankingStrategy strategy : rankingStrategies) {
            FeedRankingStrategy previous = strategies.put(strategy.sort(), strategy);
            if (previous != null) {
                // Fail fast at startup instead of silently picking one of two strategies.
                throw new IllegalStateException("Multiple feed strategies registered for sort " + strategy.sort());
            }
        }
    }

    public List<Post> listFeed(FeedSort sort, int page, int size) {
        FeedSort requested = sort == null ? FeedSort.NEW : sort;
        FeedRankingStrategy strategy = strategies.get(requested);
        if (strategy == null) {
            throw new IllegalArgumentException("No feed strategy registered for sort " + requested);
        }
        return strategy.fetch(posts, page, size);
    }
}
