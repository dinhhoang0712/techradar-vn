package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.adapters.input.SocialDtos;
import com.techpulse.techradar.features.social.ports.PostImageRepository;
import com.techpulse.techradar.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

    @Mock
    private PostImageRepository postImageRepository;

    private PostImageService service;

    @BeforeEach
    void setUp() {
        service = new PostImageService(postImageRepository);
    }

    private static String base64Of(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static SocialDtos.ImageInput image(String contentType, byte[] bytes) {
        return new SocialDtos.ImageInput(contentType, base64Of(bytes));
    }

    // ---- validate ---------------------------------------------------------

    @Test
    void validate_returnsEmptyListForNullOrEmptyInput() {
        assertThat(service.validate(null)).isEmpty();
        assertThat(service.validate(List.of())).isEmpty();
    }

    @Test
    void validate_throwsWhenMoreThanFourImages() {
        List<SocialDtos.ImageInput> five = List.of(
                image("image/png", "a".getBytes()), image("image/png", "b".getBytes()),
                image("image/png", "c".getBytes()), image("image/png", "d".getBytes()),
                image("image/png", "e".getBytes()));

        assertThatThrownBy(() -> service.validate(five))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_IMAGE"));
    }

    @Test
    void validate_throwsForInvalidBase64() {
        List<SocialDtos.ImageInput> images = List.of(new SocialDtos.ImageInput("image/png", "not-valid-base64!!"));

        assertThatThrownBy(() -> service.validate(images))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_IMAGE"));
    }

    @Test
    void validate_throwsForEmptyDecodedData() {
        List<SocialDtos.ImageInput> images = List.of(new SocialDtos.ImageInput("image/png", ""));

        assertThatThrownBy(() -> service.validate(images)).isInstanceOf(AppException.class);
    }

    @Test
    void validate_throwsForOversizedImage() {
        byte[] tooBig = new byte[3 * 1024 * 1024 + 1];
        List<SocialDtos.ImageInput> images = List.of(image("image/png", tooBig));

        assertThatThrownBy(() -> service.validate(images))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_IMAGE"));
    }

    @Test
    void validate_throwsForUnsupportedContentType() {
        List<SocialDtos.ImageInput> images = List.of(image("image/svg+xml", "<svg/>".getBytes()));

        assertThatThrownBy(() -> service.validate(images))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_IMAGE"));
    }

    @Test
    void validate_defaultsToPngWhenContentTypeIsBlank() {
        List<PostImageService.PreparedImage> result = service.validate(List.of(image("", "abc".getBytes())));
        assertThat(result).singleElement().extracting(PostImageService.PreparedImage::contentType).isEqualTo("image/png");
    }

    @Test
    void validate_acceptsAllowedTypesCaseInsensitivelyAndTrimmed() {
        List<PostImageService.PreparedImage> result = service.validate(List.of(image(" IMAGE/JPEG ", "abc".getBytes())));
        assertThat(result).singleElement().extracting(PostImageService.PreparedImage::contentType).isEqualTo("image/jpeg");
    }

    @Test
    void validate_stripsDataUrlPrefixBeforeDecoding() {
        String dataUrl = "data:image/png;base64," + base64Of("hello".getBytes());
        List<PostImageService.PreparedImage> result = service.validate(List.of(new SocialDtos.ImageInput("image/png", dataUrl)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).data()).isEqualTo("hello".getBytes());
    }

    @Test
    void validate_preservesOrderAndDecodesEachImage() {
        List<SocialDtos.ImageInput> images = List.of(
                image("image/png", "first".getBytes()), image("image/jpeg", "second".getBytes()));

        List<PostImageService.PreparedImage> result = service.validate(images);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).contentType()).isEqualTo("image/png");
        assertThat(result.get(0).data()).isEqualTo("first".getBytes());
        assertThat(result.get(1).contentType()).isEqualTo("image/jpeg");
        assertThat(result.get(1).data()).isEqualTo("second".getBytes());
    }

    // ---- persist ------------------------------------------------------------

    @Test
    void persist_noOpsForEmptyOrNullList() {
        StepVerifier.create(service.persist(UUID.randomUUID(), null)).verifyComplete();
        StepVerifier.create(service.persist(UUID.randomUUID(), List.of())).verifyComplete();
        verifyNoInteractions(postImageRepository);
    }

    @Test
    void persist_insertsEachImageWithAnIncrementingOrdinalPreservingOrder() {
        UUID postId = UUID.randomUUID();
        List<PostImageService.PreparedImage> images = List.of(
                new PostImageService.PreparedImage("image/png", "a".getBytes()),
                new PostImageService.PreparedImage("image/jpeg", "b".getBytes()));
        when(postImageRepository.insert(any(), any(), anyInt(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.persist(postId, images)).verifyComplete();

        ArgumentCaptor<Integer> ordinalCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(postImageRepository, times(2)).insert(
                any(UUID.class), eq(postId), ordinalCaptor.capture(), contentTypeCaptor.capture(), any(), any());
        assertThat(ordinalCaptor.getAllValues()).containsExactly(0, 1);
        assertThat(contentTypeCaptor.getAllValues()).containsExactly("image/png", "image/jpeg");
    }

    @Test
    void get_delegatesToRepository() {
        UUID imageId = UUID.randomUUID();
        PostImageRepository.ImageRow row = new PostImageRepository.ImageRow(UUID.randomUUID(), "image/png", "x".getBytes());
        when(postImageRepository.findById(imageId)).thenReturn(Mono.just(row));

        StepVerifier.create(service.get(imageId)).expectNext(row).verifyComplete();
        verify(postImageRepository, never()).insert(any(), any(), anyInt(), any(), any(), any());
    }
}
