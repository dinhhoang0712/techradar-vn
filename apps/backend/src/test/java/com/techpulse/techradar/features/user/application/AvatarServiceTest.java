package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.user.domain.Avatar;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.AvatarRepository;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock
    private AvatarRepository avatarRepository;
    @Mock
    private UserProfileRepository profileRepository;

    private AvatarService service;

    private String userId;
    private String validBase64;

    @BeforeEach
    void setUp() {
        service = new AvatarService(avatarRepository, profileRepository);
        userId = UUID.randomUUID().toString();
        validBase64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4});
    }

    @Test
    void upload_savesAvatarAndPointsExistingProfileAtServingUrl() {
        UserProfile existing = UserProfile.builder().userId(UUID.fromString(userId)).jobRole("Engineer").build();
        when(avatarRepository.save(eq(userId), eq("image/png"), any(byte[].class))).thenReturn(Mono.empty());
        when(profileRepository.findByUserId(userId)).thenReturn(Mono.just(existing));
        when(profileRepository.upsert(any(UserProfile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.upload(userId, "image/png", validBase64))
                .expectNext("/api/v1/user/avatar/" + userId)
                .verifyComplete();

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).upsert(captor.capture());
        assertThat(captor.getValue().getAvatarUrl()).isEqualTo("/api/v1/user/avatar/" + userId);
        assertThat(captor.getValue().getJobRole()).isEqualTo("Engineer");
    }

    @Test
    void upload_createsDefaultProfile_whenNoneExistsYet() {
        when(avatarRepository.save(eq(userId), anyString(), any(byte[].class))).thenReturn(Mono.empty());
        when(profileRepository.findByUserId(userId)).thenReturn(Mono.empty());
        when(profileRepository.upsert(any(UserProfile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.upload(userId, "image/jpeg", validBase64))
                .expectNext("/api/v1/user/avatar/" + userId)
                .verifyComplete();
    }

    @Test
    void upload_rejectsInvalidBase64_withoutTouchingRepositories() {
        StepVerifier.create(service.upload(userId, "image/png", "not-valid-base64!!"))
                .expectError(BadRequestException.class)
                .verify();

        verifyNoInteractions(avatarRepository, profileRepository);
    }

    @Test
    void upload_rejectsDisallowedContentType_withoutTouchingRepositories() {
        StepVerifier.create(service.upload(userId, "image/svg+xml", validBase64))
                .expectError(BadRequestException.class)
                .verify();

        verifyNoInteractions(avatarRepository, profileRepository);
    }

    @Test
    void upload_rejectsOversizedImage() {
        String hugeBase64 = Base64.getEncoder().encodeToString(new byte[3 * 1024 * 1024 + 1]);

        StepVerifier.create(service.upload(userId, "image/png", hugeBase64))
                .expectError(BadRequestException.class)
                .verify();

        verify(avatarRepository, never()).save(any(), any(), any());
    }

    @Test
    void upload_rejectsEmptyImage_withoutTouchingRepositories() {
        String emptyBase64 = Base64.getEncoder().encodeToString(new byte[0]);

        StepVerifier.create(service.upload(userId, "image/png", emptyBase64))
                .expectError(BadRequestException.class)
                .verify();

        verifyNoInteractions(avatarRepository, profileRepository);
    }

    @Test
    void upload_defaultsContentTypeToPng_whenContentTypeBlank() {
        when(avatarRepository.save(eq(userId), eq("image/png"), any(byte[].class))).thenReturn(Mono.empty());
        when(profileRepository.findByUserId(userId)).thenReturn(Mono.empty());
        when(profileRepository.upsert(any(UserProfile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.upload(userId, "  ", validBase64))
                .expectNext("/api/v1/user/avatar/" + userId)
                .verifyComplete();

        verify(avatarRepository).save(eq(userId), eq("image/png"), any(byte[].class));
    }

    @Test
    void get_delegatesToAvatarRepository() {
        Avatar avatar = new Avatar("image/png", new byte[]{9, 9});
        when(avatarRepository.find(userId)).thenReturn(Mono.just(avatar));

        StepVerifier.create(service.get(userId)).expectNext(avatar).verifyComplete();
    }
}
