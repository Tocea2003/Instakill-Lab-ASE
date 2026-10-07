# Task 2 – Pluggable event publishing

## Goal
`InteractionService` used to log a "like created" message directly, with a comment saying the
`DomainEventPublisher` pattern was not used. Event handling is now delegated to a
`DomainEventPublisher`, so logging, Kafka, async events, etc. can be introduced by adding a class,
without editing the service.

## Before / after

| Before | After |
|--------|-------|
| `log.debug("Like created event: ...")` inside `toggleLike` | `eventPublisher.publish(new LikeCreatedEvent(postId, userId, now))` |
| `InteractionService` owns a `Logger` | No logging or event handling in the service |
| `LikeCreatedEvent` defined but never used | `LikeCreatedEvent` is the event actually published |
| `InteractionService(comments, likes, posts, idGenerator, clock)` | extra constructor argument `DomainEventPublisher` |

## Design

- `DomainEventPublisher` (`interaction.domain`): `void publish(Object event)`.
- `NoOpDomainEventPublisher`: Null Object, `@Component`, does nothing. It is the only bean for now, so
  Spring injects it automatically and the service needs no null checks.
- `InteractionService.toggleLike` publishes a `LikeCreatedEvent` only when a like is **added**
  (not when it is removed). Publishing happens inside the same transaction as the like.

## How to add a real publisher later
Create a class implementing `DomainEventPublisher` (for example `KafkaDomainEventPublisher` or an
async one) and make it the active bean: annotate it `@Primary`, or remove `@Component` from
`NoOpDomainEventPublisher`. `InteractionService` stays unchanged.

## Files
- New: `interaction/domain/DomainEventPublisher.java`, `interaction/domain/NoOpDomainEventPublisher.java`
- Changed: `interaction/application/InteractionService.java`
- Tests (`InteractionServiceTest`): a mock publisher verifies that exactly one correct
  `LikeCreatedEvent` is published when liking and none when unliking; another test runs the service with
  `NoOpDomainEventPublisher` to show it works with no exceptions.
