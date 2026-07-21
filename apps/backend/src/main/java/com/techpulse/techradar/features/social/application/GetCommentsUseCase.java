package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.PostComment;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.shared.paging.PageRequest;
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
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);

        return commentRepository.findByPost(UUID.fromString(postId), pageRequest.size(), pageRequest.offset())
                .map(row -> new PostComment(
                        row.id().toString(),
                        new UserSummary(row.authorId().toString(), row.authorName(), row.authorAvatarUrl()),
                        row.content(),
                        row.parentCommentId() == null ? null : row.parentCommentId().toString(),
                        row.createdAt()
                ));
    }
}
