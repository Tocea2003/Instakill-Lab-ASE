package com.instakill.feed.domain;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * "Trending" ranking: most liked first, then most commented, then most recent.
 *
 * The repository only offers a "latest" query, so we over-fetch a window of recent posts
 * and sort it in memory. This is an approximation of a global trending order.
 */
@Component
public class TrendingFeedRankingStrategy implements FeedRankingStrategy {

    // Likes (desc), then comments (desc), then recency (desc).
    private static final Comparator<Post> TRENDING_ORDER = Comparator
            .comparingLong(Post::likeCount).reversed()
            .thenComparing(Comparator.comparingLong(Post::commentCount).reversed())
            .thenComparing(Comparator.comparing(Post::createdAt).reversed());

    @Override
    public FeedSort sort() {
        return FeedSort.TRENDING;
    }

    @Override
    public List<Post> fetch(PostRepository repository, int page, int size) {
        // Fetch more posts than needed so the in-memory sort has enough candidates.
        int fetchSize = Math.max(size * 2, Math.max(size, 10));
        List<Post> candidates = repository.findLatest(0, (page + 1) * fetchSize);

        return candidates.stream()
                .sorted(TRENDING_ORDER)
                .skip((long) page * size)
                .limit(size)
                .toList();
    }
}
