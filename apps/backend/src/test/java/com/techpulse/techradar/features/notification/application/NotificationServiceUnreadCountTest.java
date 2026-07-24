package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceUnreadCountTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private ReactiveRedisMessageListenerContainer redisListenerContainer;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private UserRepository userRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, redisListenerContainer, redisTemplate, new ObjectMapper(), userRepository);
    }

    @Test
    void unreadCount_withNullType_delegatesToPlainCountUnread() {
        when(repository.countUnread("user-1")).thenReturn(Mono.just(5L));

        StepVerifier.create(service.unreadCount("user-1", null))
                .expectNext(5L)
                .verifyComplete();

        verify(repository, never()).countUnreadByType(anyString(), anyString());
    }

    @Test
    void unreadCount_withType_delegatesToCountUnreadByType() {
        when(repository.countUnreadByType("user-1", "ADMIN_JOB_REPEATED_FAILURE")).thenReturn(Mono.just(2L));

        StepVerifier.create(service.unreadCount("user-1", "ADMIN_JOB_REPEATED_FAILURE"))
                .expectNext(2L)
                .verifyComplete();

        verify(repository, never()).countUnread("user-1");
    }
}
