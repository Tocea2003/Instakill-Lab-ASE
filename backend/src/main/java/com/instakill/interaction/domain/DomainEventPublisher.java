package com.instakill.interaction.domain;

/**
 * Port for publishing domain events (e.g. {@link LikeCreatedEvent}).
 *
 * Application services depend only on this interface, so how events leave the system
 * (logging, Spring events, Kafka, async queue...) is decided by whichever implementation
 * is registered as a Spring bean. Switching strategy never requires touching the service.
 */
public interface DomainEventPublisher {

    /** Publishes a domain event. Implementations must not return an error for events they ignore. */
    void publish(Object event);
}
