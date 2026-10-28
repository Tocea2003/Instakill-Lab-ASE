package com.instakill.notification.infrastructure;

import com.instakill.notification.domain.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Publishes notifications to an ntfy server (https://ntfy.sh) topic. Failures are logged, never thrown. */
@Component
@ConditionalOnProperty(name = "notification.ntfy.enabled", havingValue = "true")
public class NtfyNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationSender.class);

    private final RestClient client;
    private final String topic;

    @Autowired
    public NtfyNotificationSender(
            @Value("${notification.ntfy.base-url:https://ntfy.sh}") String baseUrl,
            @Value("${notification.ntfy.topic}") String topic) {
        this(RestClient.builder().baseUrl(baseUrl), topic);
    }

    public NtfyNotificationSender(RestClient.Builder builder, String topic) {
        this.client = builder.build();
        this.topic = topic;
    }

    @Override
    public void send(String title, String message) {
        try {
            client.post()
                    .uri("/{topic}", topic)
                    .header("Title", title)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Failed to send ntfy notification: {}", e.getMessage());
        }
    }
}
