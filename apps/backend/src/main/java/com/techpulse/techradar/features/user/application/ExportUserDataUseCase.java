package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * GDPR data-portability: assembles everything one user authored into a single downloadable
 * export (account, profile, own posts, own comments). See {@link UserDataExport} for what is
 * deliberately left out.
 */
@Component
@RequiredArgsConstructor
public class ExportUserDataUseCase {

    /** A GDPR export is a one-shot dump, not a paginated feed - large enough to cover any real user's history. */
    private static final int MAX_POSTS = 10_000;

    private final UserAccountValidator accountValidator;
    private final UserProfileRepository profileRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public Mono<UserDataExport> execute(String userId) {
        UUID id = UUID.fromString(userId);
        return accountValidator.findByIdOrThrow(userId)
                .flatMap(user -> Mono.zip(
                        profileRepository.findByUserId(userId)
                                .map(ExportUserDataUseCase::toProfile)
                                .defaultIfEmpty(emptyProfile()),
                        postRepository.findByUser(id, id, MAX_POSTS, 0)
                                .map(row -> new UserDataExport.Post(row.id(), row.content(), row.createdAt()))
                                .collectList(),
                        commentRepository.findByUser(id)
                                .map(row -> new UserDataExport.Comment(row.id(), row.content(), row.createdAt()))
                                .collectList()
                ).map(tuple -> new UserDataExport(
                        toAccount(user), tuple.getT1(), tuple.getT2(), tuple.getT3())));
    }

    private static UserDataExport.Account toAccount(User user) {
        return new UserDataExport.Account(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getStatus(), user.getSubscriptionTier(), user.getCreatedAt());
    }

    private static UserDataExport.Profile toProfile(UserProfile profile) {
        return new UserDataExport.Profile(
                profile.getJobRole(), profile.getBio(), profile.getLocation(),
                profile.getAvatarUrl(), profile.getTechnologies());
    }

    private static UserDataExport.Profile emptyProfile() {
        return new UserDataExport.Profile(null, null, null, null, List.of());
    }
}
