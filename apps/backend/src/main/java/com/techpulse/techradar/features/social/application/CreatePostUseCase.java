package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.company.application.GetCompaniesUseCase;
import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.social.adapters.input.SocialDtos;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.realtime.FeedBroadcaster;
import com.techpulse.techradar.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreatePostUseCase {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final PostRepository postRepository;
    private final PostImageService postImageService;
    private final GetCompaniesUseCase getCompaniesUseCase;
    private final MentionNotifier mentionNotifier;
    private final FeedBroadcaster feedBroadcaster;

    public Mono<String> execute(String userId, String content, List<SocialDtos.ImageInput> images,
                                 String taggedCompanyId, List<String> mentionedUserIds) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return Mono.error(new AppException("Post content must not be empty", 400, "INVALID_CONTENT"));
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            return Mono.error(new AppException("Post content too long (max " + MAX_CONTENT_LENGTH + " chars)", 400, "INVALID_CONTENT"));
        }
        if (MentionNotifier.tooMany(mentionedUserIds)) {
            // Validated before any write: failing after the post/images already exist would leave
            // a persisted post behind an error response.
            return Mono.error(new AppException(
                    "Too many mentions (max " + MentionNotifier.MAX_MENTIONS + ")", 400, "INVALID_MENTIONS"));
        }

        List<PostImageService.PreparedImage> preparedImages;
        try {
            // Also validated up front, before the post row is inserted, for the same reason.
            preparedImages = postImageService.validate(images);
        } catch (AppException e) {
            return Mono.error(e);
        }

        List<String> hashtags = HashtagParser.parse(trimmed);
        UUID postId = UUID.randomUUID();
        UUID userUuid = UUID.fromString(userId);

        return resolveTaggedCompany(taggedCompanyId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(companyOpt -> {
                    CompanyProfile company = companyOpt.orElse(null);
                    return postRepository.insert(new PostRepository.NewPost(
                            postId, userUuid, trimmed, hashtags,
                            company != null ? company.id() : null,
                            company != null ? company.name() : null,
                            company != null ? company.location() : null,
                            LocalDateTime.now()));
                })
                .then(postImageService.persist(postId, preparedImages))
                .then(broadcastNewPost(postId, userUuid))
                .then(mentionNotifier.notify(userUuid, mentionedUserIds, "bài viết", "/feed"))
                .thenReturn(postId.toString());
    }

    /** Broadcasts the freshly-created post to the live feed (Redis Pub/Sub -> SSE). Best-effort. */
    private Mono<Void> broadcastNewPost(UUID postId, UUID authorId) {
        return postRepository.findById(postId, authorId)
                .map(FeedMapper::toFeedPost)
                .doOnNext(feedBroadcaster::publishPostCreated)
                .onErrorResume(e -> {
                    log.warn("Could not broadcast new post {}", postId, e);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<CompanyProfile> resolveTaggedCompany(String taggedCompanyId) {
        if (taggedCompanyId == null || taggedCompanyId.isBlank()) {
            return Mono.empty();
        }
        return getCompaniesUseCase.all()
                .filter(c -> taggedCompanyId.equals(c.id()))
                .next()
                .switchIfEmpty(Mono.error(new AppException("Company not found", 400, "INVALID_COMPANY")));
    }
}
