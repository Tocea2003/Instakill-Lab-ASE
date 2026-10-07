package com.instakill.feed.domain;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the "newest" strategy in isolation (no FeedService involved). */
class NewestFeedRankingStrategyTest {

    private final PostRepository repository = mock(PostRepository.class);
    private final NewestFeedRankingStrategy strategy = new NewestFeedRankingStrategy();

    @Test
    void handlesNewSortMode() {
        assertThat(strategy.sort()).isEqualTo(FeedSort.NEW);
    }

    @Test
    void returnsRepositoryLatestPostsUnchanged() {
        Instant now = Instant.now();
        List<Post> posts = List.of(post(now, 1, 1), post(now.minusSeconds(20), 9, 9));
        when(repository.findLatest(0, 2)).thenReturn(posts);

        assertThat(strategy.fetch(repository, 0, 2)).isEqualTo(posts);
    }

    private Post post(Instant createdAt, long likes, long comments) {
        return new Post(UUID.randomUUID(), UUID.randomUUID(), "content", null, createdAt, createdAt, likes, comments);
    }
}
