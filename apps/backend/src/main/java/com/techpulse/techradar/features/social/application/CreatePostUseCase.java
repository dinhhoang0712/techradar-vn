package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.CompanyLookupPort;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.realtime.FeedBroadcaster;
import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.util.ContentValidator;
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
    private final CompanyLookupPort companyLookupPort;
    private final MentionNotifier mentionNotifier;
    private final FeedBroadcaster feedBroadcaster;

    public Mono<String> execute(String userId, String content, List<ImageInput> images,
                                 String taggedCompanyId, List<String> mentionedUserIds) {
        String trimmed;
        try {
            trimmed = ContentValidator.requireValidLength(content, MAX_CONTENT_LENGTH, "Post content");
        } catch (AppException e) {
            return Mono.error(e);
        }
        if (MentionNotifier.tooMany(mentionedUserIds)) {
            // Validated before any write: failing after the post/images already exist would leave
            // a persisted post behind an error response.
            return Mono.error(new BadRequestException(
                    ErrorCode.INVALID_MENTIONS, "Too many mentions (max " + MentionNotifier.MAX_MENTIONS + ")"));
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
                    CompanyLookupPort.CompanySummary company = companyOpt.orElse(null);
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

    private Mono<CompanyLookupPort.CompanySummary> resolveTaggedCompany(String taggedCompanyId) {
        if (taggedCompanyId == null || taggedCompanyId.isBlank()) {
            return Mono.empty();
        }
        return companyLookupPort.findById(taggedCompanyId)
                .switchIfEmpty(Mono.error(new BadRequestException(ErrorCode.INVALID_COMPANY, "Company not found")));
    }
}
