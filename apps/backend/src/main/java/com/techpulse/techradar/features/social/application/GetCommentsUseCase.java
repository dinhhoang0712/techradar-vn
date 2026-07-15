package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.PostComment;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetCommentsUseCase {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final CommentRepository commentRepository;

    public Flux<PostComment> execute(String postId, int page, int size) {
        int effectiveSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = Math.max(page, 0) * effectiveSize;

        return commentRepository.findByPost(UUID.fromString(postId), effectiveSize, offset)
                .map(row -> new PostComment(
                        row.id().toString(),
                        new UserSummary(row.authorId().toString(), row.authorName(), row.authorAvatarUrl()),
                        row.content(),
                        row.createdAt()
                ));
    }
}
