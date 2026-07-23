package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.ProfileSummary;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.ports.UserDirectoryRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProfileSummaryUseCaseTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserDirectoryRepository userDirectoryRepository;

    @Mock
    private PostRepository postRepository;

    private GetProfileSummaryUseCase useCase;

    private final UUID targetId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetProfileSummaryUseCase(followRepository, userDirectoryRepository, postRepository);
    }

    @Test
    void execute_assemblesTheSummaryFromAllCollaboratorsWhenViewingSomeoneElse() {
        when(userDirectoryRepository.findProfileBasics(targetId)).thenReturn(Mono.just(
                new UserDirectoryRepository.ProfileBasics("An Nguyen", "avatar.png", "Backend engineer", "Software Engineer", "Hanoi")
        ));
        when(followRepository.followerCount(targetId)).thenReturn(Mono.just(10L));
        when(followRepository.followingCount(targetId)).thenReturn(Mono.just(5L));
        when(postRepository.countByUser(targetId)).thenReturn(Mono.just(3L));
        when(followRepository.isFollowing(viewerId, targetId)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(targetId.toString(), viewerId.toString()))
                .expectNext(new ProfileSummary(
                        targetId.toString(),
                        "An Nguyen",
                        "avatar.png",
                        "Backend engineer",
                        "Software Engineer",
                        "Hanoi",
                        10L,
                        5L,
                        3L,
                        true
                ))
                .verifyComplete();
    }

    @Test
    void execute_skipsTheIsFollowingLookupAndReportsFalseWhenViewingOwnProfile() {
        when(userDirectoryRepository.findProfileBasics(targetId)).thenReturn(Mono.just(
                new UserDirectoryRepository.ProfileBasics("An Nguyen", "avatar.png", "Bio", "Engineer", "Hanoi")
        ));
        when(followRepository.followerCount(targetId)).thenReturn(Mono.just(1L));
        when(followRepository.followingCount(targetId)).thenReturn(Mono.just(2L));
        when(postRepository.countByUser(targetId)).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute(targetId.toString(), targetId.toString()))
                .expectNextMatches(summary -> !summary.isFollowing())
                .verifyComplete();

        verify(followRepository, never()).isFollowing(targetId, targetId);
    }

    @Test
    void execute_errorsWithNotFoundWhenTheTargetUserDoesNotExist() {
        when(userDirectoryRepository.findProfileBasics(targetId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(targetId.toString(), viewerId.toString()))
                .expectError(NotFoundException.class)
                .verify();

        verify(followRepository, never()).followerCount(targetId);
        verify(postRepository, never()).countByUser(targetId);
    }
}
