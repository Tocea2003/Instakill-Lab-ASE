package com.instakill.post.application;

import com.instakill.common.clock.Clock;
import com.instakill.common.id.IdGenerator;
import com.instakill.feed.application.FeedService;
import com.instakill.interaction.domain.DomainEventPublisher;
import com.instakill.interaction.domain.NoOpDomainEventPublisher;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostCreatedEvent;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostServiceTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final PostRepository posts = mock(PostRepository.class);
    private final IdGenerator ids = () -> POST_ID;
    private final Clock clock = () -> NOW;
    private final FeedService feed = mock(FeedService.class);

    @Test
    void publishesPostCreatedEventAfterSaving() {
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        when(posts.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        PostService service = new PostService(posts, ids, clock, feed, publisher);

        service.createPost(AUTHOR_ID, "hello", null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PostCreatedEvent(POST_ID, AUTHOR_ID, NOW));
    }

    @Test
    void worksWithNoOpPublisher() {
        when(posts.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        PostService service = new PostService(posts, ids, clock, feed, new NoOpDomainEventPublisher());

        assertThat(service.createPost(AUTHOR_ID, "hello", null).id()).isEqualTo(POST_ID);
    }
}
