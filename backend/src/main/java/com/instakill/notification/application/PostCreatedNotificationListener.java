package com.instakill.notification.application;

import com.instakill.notification.domain.NotificationSender;
import com.instakill.post.domain.PostCreatedEvent;
import com.instakill.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends a notification after a post is committed. Runs on the async executor, off the request thread. */
@Component
public class PostCreatedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PostCreatedNotificationListener.class);

    private final NotificationSender sender;
    private final UserRepository users;

    public PostCreatedNotificationListener(NotificationSender sender, UserRepository users) {
        this.sender = sender;
        this.users = users;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCreated(PostCreatedEvent event) {
        try {
            String username = users.findById(event.authorId()).map(u -> u.username()).orElse("unknown");
            sender.send("New post on Instakill", format(username, event));
        } catch (RuntimeException e) {
            log.warn("Could not notify about post {}: {}", event.postId(), e.getMessage());
        }
    }

    static String format(String username, PostCreatedEvent event) {
        return "User: " + username + "\nPost ID: " + event.postId() + "\nTime: " + event.occurredAt();
    }
}
