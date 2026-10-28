package com.instakill.notification.application;

import com.instakill.notification.domain.NotificationSender;
import com.instakill.post.domain.PostCreatedEvent;
import com.instakill.user.domain.User;
import com.instakill.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostCreatedNotificationListenerTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final NotificationSender sender = mock(NotificationSender.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PostCreatedNotificationListener listener = new PostCreatedNotificationListener(sender, users);
    private final PostCreatedEvent event = new PostCreatedEvent(POST_ID, AUTHOR_ID, NOW);

    @Test
    void sendsMessageWithUsernamePostIdAndTimestamp() {
        when(users.findById(AUTHOR_ID)).thenReturn(Optional.of(new User(AUTHOR_ID, "alice", "a@x.com", "hash", NOW)));

        listener.onPostCreated(event);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender).send(anyString(), message.capture());
        assertThat(message.getValue()).contains("alice", POST_ID.toString(), NOW.toString());
    }

    @Test
    void senderFailureDoesNotPropagate() {
        when(users.findById(AUTHOR_ID)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("boom")).when(sender).send(anyString(), anyString());

        assertThatCode(() -> listener.onPostCreated(event)).doesNotThrowAnyException();
    }
}
