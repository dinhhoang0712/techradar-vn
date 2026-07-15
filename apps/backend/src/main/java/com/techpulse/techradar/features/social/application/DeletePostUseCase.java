package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeletePostUseCase {

    private final PostRepository postRepository;

    public Mono<Void> execute(String postId, String userId) {
        return postRepository.deleteOwnedBy(UUID.fromString(postId), UUID.fromString(userId))
                .flatMap(deleted -> deleted
                        ? Mono.empty()
                        : Mono.error(new NotFoundException("Post not found: " + postId)));
    }
}
