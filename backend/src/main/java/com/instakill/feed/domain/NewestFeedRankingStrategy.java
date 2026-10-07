package com.instakill.feed.domain;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** "Newest first" ranking: simply delegates to the repository, which already orders by creation date. */
@Component
public class NewestFeedRankingStrategy implements FeedRankingStrategy {

    @Override
    public FeedSort sort() {
        return FeedSort.NEW;
    }

    @Override
    public List<Post> fetch(PostRepository repository, int page, int size) {
        return repository.findLatest(page, size);
    }
}
