# Task 1 – Flexible feed sorting with the Strategy pattern

## Goal
`FeedService` used to contain a `switch` on `FeedSort` and the sorting algorithms themselves.
Adding a new sort mode meant editing that class. The sorting logic now lives in interchangeable
strategy classes, so `FeedService` never has to change when a sort mode is added.

## Before / after

| Before | After |
|--------|-------|
| `FeedService.listFeed` has a `switch (sort)` | `FeedService.listFeed` looks up a strategy in a `Map<FeedSort, FeedRankingStrategy>` |
| `fetchNewestFeed` / `fetchTrendingFeed` are private methods of `FeedService` | Each algorithm is its own `@Component` class |
| `FeedService(PostRepository)` | `FeedService(PostRepository, List<FeedRankingStrategy>)` |

## Design

```
FeedService ──uses──▶ FeedRankingStrategy (interface)
                         ▲                     ▲
        NewestFeedRankingStrategy   TrendingFeedRankingStrategy
              (FeedSort.NEW)              (FeedSort.TRENDING)
```

- `FeedRankingStrategy` (`feed.domain`): `FeedSort sort()` and `List<Post> fetch(PostRepository, int page, int size)`.
- `NewestFeedRankingStrategy`: delegates to `repository.findLatest`.
- `TrendingFeedRankingStrategy`: over-fetches recent posts, then sorts by likes → comments → recency
  (all descending) and applies paging. Same behavior as the old private method.
- `FeedService`: Spring injects every `FeedRankingStrategy` bean. The constructor indexes them by
  `sort()` in an `EnumMap`. A `null` sort defaults to `NEW`. Two strategies for the same sort fail at
  startup (`IllegalStateException`); a sort with no strategy throws `IllegalArgumentException`.

## How to add a new sort mode (no change to `FeedService`)
1. Add a value to `FeedSort` (and map it in `FeedSort.from` if it should be reachable from a query string).
2. Create a `@Component` implementing `FeedRankingStrategy` that returns that value from `sort()`.

## Files
- New: `feed/domain/FeedRankingStrategy.java`, `NewestFeedRankingStrategy.java`, `TrendingFeedRankingStrategy.java`
- Changed: `feed/application/FeedService.java`
- Tests: `feed/domain/NewestFeedRankingStrategyTest`, `feed/domain/TrendingFeedRankingStrategyTest`
  (each strategy in isolation) and `feed/application/FeedServiceTest` (delegation, default sort,
  custom strategy plugged in without editing the service, error cases).
  The old `TrendingFeedRankingStrategyTest.java` actually contained a class named `FeedServiceTest`;
  it was replaced by the files above.
