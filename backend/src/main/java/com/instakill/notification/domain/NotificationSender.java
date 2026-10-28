package com.instakill.notification.domain;

/** Port for delivering a notification to an external service. */
public interface NotificationSender {

    void send(String title, String message);
}
