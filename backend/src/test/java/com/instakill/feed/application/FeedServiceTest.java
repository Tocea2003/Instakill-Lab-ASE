package com.instakill.feed.application;

import com.instakill.feed.domain.FeedRankingStrategy;
import com.instakill.feed.domain.FeedSort;
import com.instakill.feed.domain.NewestFeedRankingStrategy;
import com.instakill.feed.domain.TrendingFeedRankingStrategy;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests that FeedService only selects and delegates to the right strategy. */
class FeedServiceTest {

    private final PostRepository repository = mock(PostRepository.class);
    private final FeedService service = new FeedService(
            repository, List.of(new NewestFeedRankingStrategy(), new TrendingFeedRankingStrategy()));

    @Test
    void delegatesNewToNewestStrategy() {
        List<Post> posts = List.of(post(5));
        when(repository.findLatest(0, 5)).thenReturn(posts);

        assertThat(service.listFeed(FeedSort.NEW, 0, 5)).isEqualTo(posts);
    }

    @Test
    void delegatesTrendingToTrendingStrategy() {
        Post low = post(1);
        Post high = post(9);
        when(repository.findLatest(0, 10)).thenReturn(List.of(low, high));

        assertThat(service.listFeed(FeedSort.TRENDING, 0, 2)).containsExactly(high, low);
    }

    @Test
    void defaultsToNewWhenSortIsNull() {
        List<Post> posts = List.of(post(1));
        when(repository.findLatest(0, 5)).thenReturn(posts);

        assertThat(service.listFeed(null, 0, 5)).isEqualTo(posts);
    }

    @Test
    void usesAnyNewStrategyWithoutChangingFeedService() {
        // A custom strategy proves the service is open for extension: no FeedService edit needed.
        List<Post> marker = List.of(post(42));
        FeedRankingStrategy custom = new FeedRankingStrategy() {
            @Override
            public FeedSort sort() {
                return FeedSort.TRENDING;
            }

            @Override
            public List<Post> fetch(PostRepository repo, int page, int size) {
                return marker;
            }
        };
        FeedService customService = new FeedService(repository, List.of(new NewestFeedRankingStrategy(), custom));

        assertThat(customService.listFeed(FeedSort.TRENDING, 0, 5)).isSameAs(marker);
    }

    @Test
    void failsWhenNoStrategyIsRegisteredForSort() {
        FeedService onlyNewest = new FeedService(repository, List.of(new NewestFeedRankingStrategy()));

        assertThatThrownBy(() -> onlyNewest.listFeed(FeedSort.TRENDING, 0, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTwoStrategiesForTheSameSort() {
        List<FeedRankingStrategy> duplicates = List.of(new NewestFeedRankingStrategy(), new NewestFeedRankingStrategy());

        assertThatThrownBy(() -> new FeedService(repository, duplicates))
                .isInstanceOf(IllegalStateException.class);
    }

    private Post post(long likes) {
        Instant now = Instant.now();
        return new Post(UUID.randomUUID(), UUID.randomUUID(), "content", null, now, now, likes, 0);
    }
}
