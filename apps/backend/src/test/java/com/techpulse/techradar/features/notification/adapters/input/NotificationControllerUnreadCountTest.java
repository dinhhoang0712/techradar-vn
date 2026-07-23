package com.techpulse.techradar.features.notification.adapters.input;

import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerUnreadCountTest {

    @Mock
    private NotificationService notificationService;

    private static Context withUser(String userId) {
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContext securityContext = new SecurityContextImpl(auth);
        return ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));
    }

    @Test
    void unreadCount_withNoTypeParam_passesNullThrough() {
        NotificationController controller = new NotificationController(notificationService);
        when(notificationService.unreadCount(eq("user-1"), eq(null))).thenReturn(Mono.just(7L));

        StepVerifier.create(controller.unreadCount(null).contextWrite(withUser("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Long> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).isEqualTo(7L);
                })
                .verifyComplete();
    }

    @Test
    void unreadCount_withTypeParam_passesItThrough() {
        NotificationController controller = new NotificationController(notificationService);
        when(notificationService.unreadCount("user-1", "ADMIN_JOB_REPEATED_FAILURE")).thenReturn(Mono.just(2L));

        StepVerifier.create(controller.unreadCount("ADMIN_JOB_REPEATED_FAILURE").contextWrite(withUser("user-1")))
                .assertNext(response -> {
                    ApiResponse<Long> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).isEqualTo(2L);
                })
                .verifyComplete();
    }
}
