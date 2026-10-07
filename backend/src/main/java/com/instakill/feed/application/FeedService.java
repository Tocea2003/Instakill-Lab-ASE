package com.instakill.feed.application;

import com.instakill.feed.domain.FeedSort;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class FeedService {

    private final PostRepository posts;

    public FeedService(PostRepository posts) {
        this.posts = posts;
    }

    public List<Post> listFeed(FeedSort sort, int page, int size) {
        if (sort == null) {
            sort = FeedSort.NEW;
        }
        
        return switch (sort) {
            case NEW -> fetchNewestFeed(page, size);
            case TRENDING -> fetchTrendingFeed(page, size);
        };
    }
    
    private List<Post> fetchNewestFeed(int page, int size) {
        return posts.findLatest(page, size);
    }
    
    private List<Post> fetchTrendingFeed(int page, int size) {
        // Fetch more posts than needed to allow for proper trending sorting
        int fetchSize = Math.max(size * 2, Math.max(size, 10));
        List<Post> candidates = posts.findLatest(0, (page + 1) * fetchSize);
        
        // Sort by likes (descending), then comments (descending), then recency (descending)
        Comparator<Post> comparator = Comparator
                .comparingLong(Post::likeCount).reversed()
                .thenComparing(Comparator.comparingLong(Post::commentCount).reversed())
                .thenComparing(Comparator.comparing(Post::createdAt).reversed());
                
        return candidates.stream()
                .sorted(comparator)
                .skip((long) page * size)
                .limit(size)
                .toList();
    }
}
