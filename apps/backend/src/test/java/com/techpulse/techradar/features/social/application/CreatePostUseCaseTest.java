package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.adapters.input.SocialDtos;
import com.techpulse.techradar.features.social.ports.CompanyLookupPort;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.realtime.FeedBroadcaster;
import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePostUseCaseTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostImageService postImageService;
    @Mock
    private CompanyLookupPort companyLookupPort;
    @Mock
    private MentionNotifier mentionNotifier;
    @Mock
    private FeedBroadcaster feedBroadcaster;

    private CreatePostUseCase useCase;

    private final String userId = UUID.randomUUID().toString();

    private PostRepository.FeedRow sampleFeedRow(UUID postId) {
        return new PostRepository.FeedRow(
                postId, UUID.fromString(userId), "Test User", null, "content", LocalDateTime.now(),
                0, 0, false, List.of(), List.of(), null, null, null);
    }

    @BeforeEach
    void setUp() {
        useCase = new CreatePostUseCase(postRepository, postImageService, companyLookupPort, mentionNotifier, feedBroadcaster);
        lenient().when(postRepository.insert(any())).thenReturn(Mono.empty());
        lenient().when(postRepository.findById(any(), any())).thenAnswer(inv -> Mono.just(sampleFeedRow(inv.getArgument(0))));
        lenient().when(postImageService.validate(any())).thenReturn(List.of());
        lenient().when(postImageService.persist(any(), any())).thenReturn(Mono.empty());
        lenient().when(mentionNotifier.notify(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void execute_rejectsEmptyContent() {
        StepVerifier.create(useCase.execute(userId, "   ", null, null, null))
                .expectError()
                .verify();
    }

    @Test
    void execute_rejectsContentTooLong() {
        StepVerifier.create(useCase.execute(userId, "x".repeat(2001), null, null, null))
                .expectErrorSatisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_CONTENT"))
                .verify();
    }

    @Test
    void execute_parsesVietnameseAndDedupesHashtagsFromContent() {
        StepVerifier.create(useCase.execute(userId, "Học #Java và #java hôm nay, dùng #ReactJS và #côngNghệ", null, null, null))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<PostRepository.NewPost> captor = ArgumentCaptor.forClass(PostRepository.NewPost.class);
        verify(postRepository).insert(captor.capture());
        assertThat(captor.getValue().hashtags()).containsExactly("java", "reactjs", "côngnghệ");
    }

    @Test
    void execute_persistsValidatedImagesAfterInsertingThePost() {
        List<SocialDtos.ImageInput> images = List.of(new SocialDtos.ImageInput("image/png", "abc"));
        List<PostImageService.PreparedImage> prepared = List.of(new PostImageService.PreparedImage("image/png", new byte[]{1, 2, 3}));
        when(postImageService.validate(images)).thenReturn(prepared);

        useCase.execute(userId, "With a photo", images, null, null).block();

        verify(postImageService).persist(any(UUID.class), eq(prepared));
    }

    @Test
    void execute_rejectsInvalidImagesBeforeInsertingThePost() {
        List<SocialDtos.ImageInput> images = List.of(new SocialDtos.ImageInput("image/png", "not-valid-base64"));
        when(postImageService.validate(images))
                .thenThrow(new BadRequestException(ErrorCode.INVALID_IMAGE, "Image empty or too large (max 3MB)"));

        StepVerifier.create(useCase.execute(userId, "Bad image", images, null, null))
                .expectErrorSatisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_IMAGE"))
                .verify();

        verify(postRepository, never()).insert(any());
    }

    @Test
    void execute_rejectsTooManyMentionsBeforeInsertingThePost() {
        List<String> tooMany = IntStream.range(0, 11).mapToObj(i -> UUID.randomUUID().toString()).toList();

        StepVerifier.create(useCase.execute(userId, "Hi everyone", null, null, tooMany))
                .expectErrorSatisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_MENTIONS"))
                .verify();

        verify(postRepository, never()).insert(any());
        verify(mentionNotifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void execute_resolvesAndSnapshotsTaggedCompany() {
        String companyId = "neo4j-company-1";
        when(companyLookupPort.findById(companyId)).thenReturn(
                Mono.just(new CompanyLookupPort.CompanySummary(companyId, "Acme Corp", "Hà Nội")));

        useCase.execute(userId, "Working at Acme!", null, companyId, null).block();

        ArgumentCaptor<PostRepository.NewPost> captor = ArgumentCaptor.forClass(PostRepository.NewPost.class);
        verify(postRepository).insert(captor.capture());
        assertThat(captor.getValue().taggedCompanyId()).isEqualTo(companyId);
        assertThat(captor.getValue().taggedCompanyName()).isEqualTo("Acme Corp");
        assertThat(captor.getValue().taggedCompanyLocation()).isEqualTo("Hà Nội");
    }

    @Test
    void execute_rejectsAnUnknownTaggedCompanyId() {
        when(companyLookupPort.findById("does-not-exist")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId, "Tagging a ghost company", null, "does-not-exist", null))
                .expectErrorSatisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_COMPANY"))
                .verify();

        verify(postRepository, never()).insert(any());
    }

    @Test
    void execute_forwardsMentionedUserIdsToMentionNotifier() {
        List<String> mentioned = List.of(UUID.randomUUID().toString());

        useCase.execute(userId, "Hi @someone", null, null, mentioned).block();

        verify(mentionNotifier).notify(UUID.fromString(userId), mentioned, "bài viết", "/feed");
    }

    @Test
    void execute_broadcastsTheNewPostToTheLiveFeed() {
        String postId = useCase.execute(userId, "Broadcast me", null, null, null).block();

        verify(feedBroadcaster).publishPostCreated(argThat(post -> post.id().equals(postId)));
    }

    @Test
    void execute_stillSucceedsWhenBroadcastLookupFails() {
        when(postRepository.findById(any(), any())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(useCase.execute(userId, "Resilient post", null, null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(feedBroadcaster, never()).publishPostCreated(any());
    }
}
