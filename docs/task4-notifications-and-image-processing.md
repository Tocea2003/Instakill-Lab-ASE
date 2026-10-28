# Task 4 – Post notifications and image processing

## Goal
1. When a post is created, asynchronously send an HTTP request to a public notification service (ntfy.sh) with the username, post ID and timestamp.
2. When an image is uploaded, process it before saving: scale to a maximum width of 300 px (aspect ratio kept) and add an "Instakill Demo" watermark at the bottom-right. The processing lives in its own module so it can later run as a worker.

## Before / after

| | Before | After |
|---|---|---|
| `PostService.createPost` | Saves and returns | Also publishes `PostCreatedEvent` through `DomainEventPublisher` |
| Domain event publisher | Only `NoOpDomainEventPublisher` | `SpringDomainEventPublisher` (`@Primary`) forwards to Spring's event bus; the no-op stays as the fallback |
| Notification | None | `PostCreatedNotificationListener` (async, after commit) -> `NotificationSender` port -> `NtfyNotificationSender` |
| Upload | Bytes copied unchanged | jpg/jpeg/png go through `ImageProcessor` first |

## Design
- **Event-driven notification.** `PostService` only knows `DomainEventPublisher` (Task 2). `PostCreatedNotificationListener` is `@Async` and `@TransactionalEventListener(AFTER_COMMIT)`, so a rolled-back post never notifies and the request thread never waits for the HTTP call.
- **Port and adapters.** `NotificationSender` has two implementations chosen by `notification.ntfy.enabled`: `NtfyNotificationSender` (`RestClient`, POST to `{base-url}/{topic}`) and `NoOpNotificationSender` (default, so dev and tests make no network calls). Failures are logged and swallowed.
- **Async.** `AsyncConfig` enables `@Async` with a small bounded `ThreadPoolTaskExecutor`.
- **Image processing module.** `media.processing.ImageProcessor` is bytes in, bytes out. `AwtImageProcessor` uses `javax.imageio` and `Graphics2D` (no new dependency), never upscales, and reads `media.processing.max-width` and `media.processing.watermark`. `MediaStorageService` depends only on the interface and returns 400 if a file with an image extension is not a readable image.
- **Limitation.** gif, webp and video are stored unchanged: ImageIO cannot write webp and re-encoding a GIF would drop its animation.

## How to extend
- **Another channel (email, Slack):** implement `NotificationSender` and mark it `@Primary` (or switch on a property); the listener and `PostService` do not change.
- **More events to notify:** add another `@Async @TransactionalEventListener` method.
- **Run image processing as a worker:** implement `ImageProcessor` so it enqueues the bytes and returns, or call a separate service; `MediaStorageService` is untouched. The byte-in/byte-out contract is what makes the move possible.

## Configuration
```yaml
notification:
  ntfy:
    enabled: false        # set true to call ntfy
    base-url: https://ntfy.sh
    topic: instakill-demo # pick a hard-to-guess topic; topics on ntfy.sh are public
media:
  processing:
    max-width: 300
    watermark: Instakill Demo
```

## Files
**New:** `post/domain/PostCreatedEvent`, `infrastructure/config/SpringDomainEventPublisher`, `infrastructure/config/AsyncConfig`, `notification/domain/NotificationSender`, `notification/application/PostCreatedNotificationListener`, `notification/infrastructure/NtfyNotificationSender`, `notification/infrastructure/NoOpNotificationSender`, `media/processing/ImageProcessor`, `media/processing/AwtImageProcessor`.

**Changed:** `post/application/PostService` (event publisher), `media/application/MediaStorageService` (image processor), `application.yaml`.

**Tests:** `PostServiceTest`, `PostCreatedNotificationListenerTest`, `NtfyNotificationSenderTest`, `AwtImageProcessorTest`; updated `MediaStorageServiceTest` and `MediaIntegrationTest`.
