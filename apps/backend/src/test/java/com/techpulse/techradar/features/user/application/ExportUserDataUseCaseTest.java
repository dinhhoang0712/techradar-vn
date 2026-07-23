package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportUserDataUseCaseTest {

    @Mock
    private UserAccountValidator accountValidator;
    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;

    private ExportUserDataUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExportUserDataUseCase(accountValidator, profileRepository, postRepository, commentRepository);
    }

    @Test
    void execute_assemblesAccountProfilePostsAndComments() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId).email("dev@example.com").fullName("Dev").role("user")
                .status("active").subscriptionTier("free").createdAt(LocalDateTime.now())
                .build();
        UserProfile profile = UserProfile.builder()
                .userId(userId).jobRole("Backend Engineer").bio("bio").location("Hanoi")
                .avatarUrl("avatar.png").technologies(List.of("Java", "Kotlin"))
                .build();
        PostRepository.FeedRow post = new PostRepository.FeedRow(
                UUID.randomUUID(), userId, "Dev", null, "my post", LocalDateTime.now(),
                0, 0, false, List.of(), List.of(), null, null, null);
        CommentRepository.CommentRow comment = new CommentRepository.CommentRow(
                UUID.randomUUID(), userId, "Dev", null, "my comment", null, LocalDateTime.now());

        when(accountValidator.findByIdOrThrow(userId.toString())).thenReturn(Mono.just(user));
        when(profileRepository.findByUserId(userId.toString())).thenReturn(Mono.just(profile));
        when(postRepository.findByUser(userId, userId, 10_000, 0)).thenReturn(Flux.just(post));
        when(commentRepository.findByUser(userId)).thenReturn(Flux.just(comment));

        StepVerifier.create(useCase.execute(userId.toString()))
                .assertNext(export -> {
                    assertThat(export.account().email()).isEqualTo("dev@example.com");
                    assertThat(export.profile().jobRole()).isEqualTo("Backend Engineer");
                    assertThat(export.profile().technologies()).containsExactly("Java", "Kotlin");
                    assertThat(export.posts()).hasSize(1);
                    assertThat(export.posts().get(0).content()).isEqualTo("my post");
                    assertThat(export.comments()).hasSize(1);
                    assertThat(export.comments().get(0).content()).isEqualTo("my comment");
                })
                .verifyComplete();
    }

    @Test
    void execute_defaultsToEmptyProfile_whenNoneExists() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("dev@example.com").build();
        when(accountValidator.findByIdOrThrow(userId.toString())).thenReturn(Mono.just(user));
        when(profileRepository.findByUserId(userId.toString())).thenReturn(Mono.empty());
        when(postRepository.findByUser(userId, userId, 10_000, 0)).thenReturn(Flux.empty());
        when(commentRepository.findByUser(userId)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(userId.toString()))
                .assertNext(export -> {
                    assertThat(export.profile().jobRole()).isNull();
                    assertThat(export.profile().technologies()).isEmpty();
                    assertThat(export.posts()).isEmpty();
                    assertThat(export.comments()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void execute_propagatesNotFound_whenUserMissing() {
        String userId = UUID.randomUUID().toString();
        when(accountValidator.findByIdOrThrow(userId)).thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(useCase.execute(userId)).expectError(NotFoundException.class).verify();
    }
}
