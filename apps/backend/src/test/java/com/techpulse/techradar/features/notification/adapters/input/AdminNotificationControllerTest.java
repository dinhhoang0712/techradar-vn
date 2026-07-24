package com.techpulse.techradar.features.notification.adapters.input;

import com.techpulse.techradar.features.notification.application.SendAdminNotificationUseCase;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationControllerTest {

    @Mock
    private SendAdminNotificationUseCase sendAdminNotificationUseCase;
    @Mock
    private AuditLogService auditLogService;

    private AdminNotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminNotificationController(sendAdminNotificationUseCase, auditLogService);
        lenient().when(auditLogService.record(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void send_reportsRecipientCount_forBroadcast() {
        AdminNotificationController.SendNotificationRequest request = new AdminNotificationController.SendNotificationRequest();
        request.setTitle("Bảo trì hệ thống");
        request.setBody("Hệ thống sẽ bảo trì lúc 2h sáng mai.");

        when(sendAdminNotificationUseCase.execute(eq("Bảo trì hệ thống"), eq("Hệ thống sẽ bảo trì lúc 2h sáng mai."), isNull(), isNull()))
                .thenReturn(Mono.just(37L));

        StepVerifier.create(controller.send(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    ApiResponse<Map<String, Object>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData()).containsEntry("recipients", 37L);
                    assertThat(body.getMessage()).contains("37");
                })
                .verifyComplete();

        verify(auditLogService).record(eq("NOTIFICATION_SEND"), eq("notification"), isNull(), any());
    }

    @Test
    void send_surfacesValidationError_asBadRequest() {
        AdminNotificationController.SendNotificationRequest request = new AdminNotificationController.SendNotificationRequest();
        request.setBody("body only, no title");

        when(sendAdminNotificationUseCase.execute(isNull(), eq("body only, no title"), isNull(), isNull()))
                .thenReturn(Mono.error(new IllegalArgumentException("title is required")));

        StepVerifier.create(controller.send(request))
                .assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(400))
                .verifyComplete();
    }
}
