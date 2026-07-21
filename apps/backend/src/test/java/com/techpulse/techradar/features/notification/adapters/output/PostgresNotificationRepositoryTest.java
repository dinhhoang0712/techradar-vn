package com.techpulse.techradar.features.notification.adapters.output;

import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.user.domain.NotificationRecipient;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Trend/job-match/roadmap subscriber lookups used to run raw SQL against {@code user_profile}
 * directly from the {@code notification} feature — a feature-boundary violation, since that table
 * belongs to the {@code user} feature. They now delegate to {@link UserProfileRepository} and only
 * remap {@link NotificationRecipient} to this feature's own {@link TrendSubscriber}. These tests
 * pin that delegation (and the field mapping) so the boundary doesn't quietly regress.
 */
@ExtendWith(MockitoExtension.class)
class PostgresNotificationRepositoryTest {

    @Mock
    private DatabaseClient dbClient;

    @Mock
    private UserProfileRepository userProfileRepository;

    private PostgresNotificationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresNotificationRepository(dbClient, userProfileRepository);
    }

    @Test
    void findTrendSubscribers_delegatesToUserProfileRepositoryAndMapsFields() {
        UUID userId = UUID.randomUUID();
        NotificationRecipient recipient = new NotificationRecipient(userId, "dev@example.com", true, false);
        when(userProfileRepository.findSubscribersByTechnology("Kotlin")).thenReturn(Flux.just(recipient));

        StepVerifier.create(repository.findTrendSubscribers("Kotlin"))
                .expectNextMatches(sub -> sub.userId().equals(userId)
                        && sub.email().equals("dev@example.com")
                        && sub.notifyInapp()
                        && !sub.notifyEmail())
                .verifyComplete();

        verify(userProfileRepository).findSubscribersByTechnology("Kotlin");
        verifyNoMoreInteractions(userProfileRepository);
    }

    @Test
    void findJobMatchSubscribers_delegatesToUserProfileRepositoryWithSameTechnologyList() {
        UUID userId = UUID.randomUUID();
        List<String> technologies = List.of("Java", "React");
        NotificationRecipient recipient = new NotificationRecipient(userId, "match@example.com", false, true);
        when(userProfileRepository.findSubscribersByAnyTechnology(eq(technologies))).thenReturn(Flux.just(recipient));

        StepVerifier.create(repository.findJobMatchSubscribers(technologies))
                .expectNextMatches(sub -> sub.userId().equals(userId)
                        && sub.email().equals("match@example.com")
                        && !sub.notifyInapp()
                        && sub.notifyEmail())
                .verifyComplete();

        verify(userProfileRepository).findSubscribersByAnyTechnology(technologies);
        verifyNoMoreInteractions(userProfileRepository);
    }

    @Test
    void findRoadmapCandidates_delegatesToUserProfileRepository() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(userProfileRepository.findSubscribersWithAnyTechnology()).thenReturn(Flux.just(
                new NotificationRecipient(first, "a@example.com", true, true),
                new NotificationRecipient(second, "b@example.com", true, true)));

        StepVerifier.create(repository.findRoadmapCandidates())
                .expectNextMatches(sub -> sub.userId().equals(first))
                .expectNextMatches(sub -> sub.userId().equals(second))
                .verifyComplete();

        verify(userProfileRepository).findSubscribersWithAnyTechnology();
        verifyNoMoreInteractions(userProfileRepository);
    }
}
