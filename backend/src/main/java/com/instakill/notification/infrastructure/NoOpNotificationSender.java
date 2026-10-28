package com.instakill.notification.infrastructure;

import com.instakill.notification.domain.NotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default sender: used unless {@code notification.ntfy.enabled=true}. */
@Component
@ConditionalOnProperty(name = "notification.ntfy.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpNotificationSender implements NotificationSender {

    @Override
    public void send(String title, String message) {
        // Intentionally empty.
    }
}
