package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
import com.techpulse.techradar.shared.redis.SecurityStampService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserAccountValidator accountValidator;
    @Mock
    private SecurityStampService securityStampService;

    private ProfileService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new ProfileService(userRepository, profileRepository, passwordEncoder, accountValidator, securityStampService);
        user = User.builder()
                .id(UUID.randomUUID())
                .email("dev@example.com")
                .fullName("Dev")
                .subscriptionTier("FREE")
                .passwordHash("old-hash")
                .build();
    }

    @Test
    void getProfile_delegatesToAccountValidator() {
        when(accountValidator.findByIdOrThrow(user.getId().toString())).thenReturn(Mono.just(user));

        StepVerifier.create(service.getProfile(user.getId().toString())).expectNext(user).verifyComplete();
    }

    @Test
    void getProfileData_combinesUserAndExistingProfile() {
        UserProfile profile = UserProfile.builder().userId(user.getId()).jobRole("Backend Engineer").build();
        when(accountValidator.findByIdOrThrow(user.getId().toString())).thenReturn(Mono.just(user));
        when(profileRepository.findByUserId(user.getId().toString())).thenReturn(Mono.just(profile));

        StepVerifier.create(service.getProfileData(user.getId().toString()))
                .assertNext(data -> {
                    assertThat(data.user()).isEqualTo(user);
                    assertThat(data.profile()).isEqualTo(profile);
                })
                .verifyComplete();
    }

    @Test
    void getProfileData_defaultsToEmptyProfile_whenNoneExists() {
        when(accountValidator.findByIdOrThrow(user.getId().toString())).thenReturn(Mono.just(user));
        when(profileRepository.findByUserId(user.getId().toString())).thenReturn(Mono.empty());

        StepVerifier.create(service.getProfileData(user.getId().toString()))
                .assertNext(data -> {
                    assertThat(data.profile().getUserId()).isEqualTo(user.getId());
                    assertThat(data.profile().getNotifyInapp()).isTrue();
                    assertThat(data.profile().getNotifyEmail()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void getProfileData_propagatesNotFound_whenUserMissing() {
        String userId = UUID.randomUUID().toString();
        when(accountValidator.findByIdOrThrow(userId)).thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(service.getProfileData(userId)).expectError(NotFoundException.class).verify();
    }

    @Test
    void updateProfile_appliesAccountAndProfileChanges_withoutTouchingEmailValidation() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("New Name")
                .password("newpassword")
                .subscriptionTier("PRO")
                .jobRole("Staff Engineer")
                .technologies(List.of("Java", "Kotlin"))
                .build();
        when(accountValidator.findByIdOrThrow(user.getId().toString())).thenReturn(Mono.just(user));
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(securityStampService.set(eq(user.getId().toString()), any(UUID.class))).thenReturn(Mono.empty());
        when(profileRepository.findByUserId(user.getId().toString())).thenReturn(Mono.empty());
        when(profileRepository.upsert(any(UserProfile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.updateProfile(user.getId().toString(), request))
                .assertNext(data -> {
                    assertThat(data.user().getFullName()).isEqualTo("New Name");
                    assertThat(data.user().getPasswordHash()).isEqualTo("new-hash");
                    assertThat(data.user().getSubscriptionTier()).isEqualTo("PRO");
                    assertThat(data.profile().getJobRole()).isEqualTo("Staff Engineer");
                    assertThat(data.profile().getTechnologies()).containsExactly("Java", "Kotlin");
                })
                .verifyComplete();

        verify(accountValidator, never()).validateEmailUnique(any(), any());
        verify(securityStampService).set(eq(user.getId().toString()), any(UUID.class));
    }

    @Test
    void updateProfile_validatesEmailUniqueness_whenEmailChanges() {
        UpdateProfileRequest request = UpdateProfileRequest.builder().email("new@example.com").build();
        when(accountValidator.findByIdOrThrow(user.getId().toString())).thenReturn(Mono.just(user));
        when(accountValidator.validateEmailUnique("new@example.com", user.getId().toString())).thenReturn(Mono.empty());
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(securityStampService.set(eq(user.getId().toString()), any(UUID.class))).thenReturn(Mono.empty());
        when(profileRepository.findByUserId(user.getId().toString())).thenReturn(Mono.empty());
        when(profileRepository.upsert(any(UserProfile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.updateProfile(user.getId().toString(), request))
                .assertNext(data -> assertThat(data.user().getEmail()).isEqualTo("new@example.com"))
                .verifyComplete();

        verify(securityStampService).set(eq(user.getId().toString()), any(UUID.class));
    }

    @Test
    void updateProfile_rejectsDuplicateEmail_withoutSavingAnything() {
        UpdateProfileRequest request = UpdateProfileRequest.builder().email("taken@example.com").build();
        ConflictException conflict = new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered");
        when(accountValidator.findByIdOrThrow(user.getId().toString())).thenReturn(Mono.just(user));
        when(accountValidator.validateEmailUnique("taken@example.com", user.getId().toString()))
                .thenReturn(Mono.error(conflict));

        StepVerifier.create(service.updateProfile(user.getId().toString(), request))
                .expectErrorMatches(e -> e == conflict)
                .verify();

        verify(userRepository, never()).save(any());
        verify(profileRepository, never()).upsert(any());
    }

    @Test
    void updateProfile_keepsExistingProfileFields_whenRequestOmitsThem() {
        UserProfile existingProfile = UserProfile.builder()
                .userId(user.getId())
                .jobRole("Backend Engineer")
                .location("Hanoi")
                .bio("bio")
                .avatarUrl("url")
                .technologies(List.of("Java"))
                .notifyInapp(false)
                .notifyEmail(false)
                .build();
        UpdateProfileRequest request = UpdateProfileRequest.builder().build();
        when(accountValidator.findByIdOrThrow(user.getId().toString())).thenReturn(Mono.just(user));
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(profileRepository.findByUserId(user.getId().toString())).thenReturn(Mono.just(existingProfile));
        when(profileRepository.upsert(any(UserProfile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.updateProfile(user.getId().toString(), request))
                .assertNext(data -> {
                    assertThat(data.profile().getJobRole()).isEqualTo("Backend Engineer");
                    assertThat(data.profile().getLocation()).isEqualTo("Hanoi");
                    assertThat(data.profile().getNotifyInapp()).isFalse();
                    assertThat(data.profile().getNotifyEmail()).isFalse();
                })
                .verifyComplete();
    }
}
