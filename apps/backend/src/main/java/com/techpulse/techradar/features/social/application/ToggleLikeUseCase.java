package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ToggleLikeUseCase {

    private final PostRepository postRepository;

    public Mono<Void> like(String postId, String userId) {
        return postRepository.like(UUID.fromString(postId), UUID.fromString(userId));
    }

    public Mono<Void> unlike(String postId, String userId) {
        return postRepository.unlike(UUID.fromString(postId), UUID.fromString(userId));
    }
}
