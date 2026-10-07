package com.instakill.interaction.domain;

import org.springframework.stereotype.Component;

/**
 * Null Object implementation: accepts every event and does nothing.
 *
 * It is the default publisher, so services can always call {@code publish(...)} without
 * null checks. To use a real publisher later, register another bean implementing
 * {@link DomainEventPublisher} (and remove this one or mark the new one {@code @Primary}).
 */
@Component
public class NoOpDomainEventPublisher implements DomainEventPublisher {

    @Override
    public void publish(Object event) {
        // Intentionally empty.
    }
}
