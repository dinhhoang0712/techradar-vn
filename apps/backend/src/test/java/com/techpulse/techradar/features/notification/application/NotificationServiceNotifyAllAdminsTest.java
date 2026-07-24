package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceNotifyAllAdminsTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private ReactiveRedisMessageListenerContainer redisListenerContainer;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private UserRepository userRepository;
    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, redisListenerContainer, redisTemplate, new ObjectMapper(), userRepository);
    }

    private static User admin(String email) {
        return User.builder().id(UUID.randomUUID()).email(email).role("admin").build();
    }

    @Test
    void notifyAllAdmins_savesOneNotificationPerAdmin() {
        User admin1 = admin("a@example.com");
        User admin2 = admin("b@example.com");
        when(userRepository.findAdmins()).thenReturn(Flux.just(admin1, admin2));
        when(repository.insert(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.notifyAllAdmins("ADMIN_ANALYTICS_REBUILD_DONE", "Đã chạy lại phân tích", "120 dòng", "/admin/automation"))
                .verifyComplete();

        verify(repository, org.mockito.Mockito.times(2)).insert(notificationCaptor.capture());
        List<Notification> saved = notificationCaptor.getAllValues();
        assertThat(saved).extracting(Notification::getUserId).containsExactlyInAnyOrder(admin1.getId(), admin2.getId());
        assertThat(saved.get(0).getType()).isEqualTo("ADMIN_ANALYTICS_REBUILD_DONE");
        assertThat(saved.get(0).getTitle()).isEqualTo("Đã chạy lại phân tích");
        assertThat(saved.get(0).getBody()).isEqualTo("120 dòng");
        assertThat(saved.get(0).getLink()).isEqualTo("/admin/automation");
        assertThat(saved.get(0).isRead()).isFalse();
    }

    @Test
    void notifyAllAdmins_doesNothing_whenNoAdminsExist() {
        when(userRepository.findAdmins()).thenReturn(Flux.empty());

        StepVerifier.create(service.notifyAllAdmins("ADMIN_ANALYTICS_REBUILD_DONE", "title", "body", "/link"))
                .verifyComplete();

        verify(repository, never()).insert(any());
    }
}
