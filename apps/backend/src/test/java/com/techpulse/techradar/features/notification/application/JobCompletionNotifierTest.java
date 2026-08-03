package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.EmailSender;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.event.ClusteringCompletedEvent;
import com.techpulse.techradar.features.notification.event.CrawlerCompletedEvent;
import com.techpulse.techradar.features.notification.event.JobCompletedEvent;
import com.techpulse.techradar.features.system.application.CmsService;
import com.techpulse.techradar.features.system.application.DataPlatformJobStatusService;
import com.techpulse.techradar.features.system.domain.CmsContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobCompletionNotifierTest {

    @Mock
    private ReactiveRedisMessageListenerContainer redisListenerContainer;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DataPlatformJobStatusService dataPlatformJobStatusService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private CmsService cmsService;
    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private JobCompletionNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new JobCompletionNotifier(redisListenerContainer, new ObjectMapper(), userRepository,
                notificationService, dataPlatformJobStatusService, emailSender, cmsService);
    }

    private static User admin(String email) {
        return User.builder().id(UUID.randomUUID()).email(email).role("admin").build();
    }

    private static Map<String, Object> run(String status) {
        return Map.of("status", status);
    }

    @Test
    void onJobCompleted_notifiesEveryAdminWithSuccessCopy_whenStatusIsSuccess() {
        User admin1 = admin("a@example.com");
        User admin2 = admin("b@example.com");
        when(userRepository.findAdmins()).thenReturn(Flux.just(admin1, admin2));
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        notifier.onJobCompleted(new JobCompletedEvent("tech_dedup", "success", 42, null));

        verify(notificationService, times(2)).save(notificationCaptor.capture());
        List<Notification> saved = notificationCaptor.getAllValues();
        assertThat(saved).extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(admin1.getId(), admin2.getId());
        assertThat(saved.get(0).getType()).isEqualTo("ADMIN_JOB_DONE");
        assertThat(saved.get(0).getTitle()).isEqualTo("Job hoàn tất: tech_dedup");
        assertThat(saved.get(0).getBody()).isEqualTo("42 dòng đã xử lý.");
        assertThat(saved.get(0).getLink()).isEqualTo("/admin/automation");
        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    void onJobCompleted_escalatesWithEmail_whenLast3RunsAllFailed() {
        User admin = admin("admin@example.com");
        when(userRepository.findAdmins()).thenReturn(Flux.just(admin));
        when(dataPlatformJobStatusService.findRunHistory("neo4j_enricher", 3, 0))
                .thenReturn(Flux.just(run("failed"), run("failed"), run("failed")));
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(emailSender.sendNotification(eq("admin@example.com"), anyString(), anyString())).thenReturn(Mono.empty());

        notifier.onJobCompleted(new JobCompletedEvent("neo4j_enricher", "failed", null, "Neo4j timeout"));

        verify(notificationService).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo("ADMIN_JOB_REPEATED_FAILURE");
        assertThat(saved.getTitle()).isEqualTo("Job liên tục thất bại (3 lần liên tiếp): neo4j_enricher");
        verify(emailSender).sendNotification("admin@example.com", saved.getTitle(), saved.getBody());
    }

    @Test
    void onJobCompleted_doesNotEscalate_whenOnly2Of3RecentRunsFailed() {
        User admin = admin("admin@example.com");
        when(userRepository.findAdmins()).thenReturn(Flux.just(admin));
        when(dataPlatformJobStatusService.findRunHistory("tech_dedup", 3, 0))
                .thenReturn(Flux.just(run("failed"), run("success"), run("failed")));
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        notifier.onJobCompleted(new JobCompletedEvent("tech_dedup", "failed", null, "Some error"));

        verify(notificationService).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("ADMIN_JOB_FAILED");
        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    void onJobCompleted_doesNotEscalate_whenJobHasFewerThan3RunsOnRecord() {
        User admin = admin("admin@example.com");
        when(userRepository.findAdmins()).thenReturn(Flux.just(admin));
        when(dataPlatformJobStatusService.findRunHistory("embed_trigger", 3, 0))
                .thenReturn(Flux.just(run("failed"), run("failed")));
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        notifier.onJobCompleted(new JobCompletedEvent("embed_trigger", "failed", null, "boom"));

        verify(notificationService).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("ADMIN_JOB_FAILED");
        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    void onJobCompleted_doesNothing_whenNoAdminsExist() {
        when(userRepository.findAdmins()).thenReturn(Flux.empty());

        notifier.onJobCompleted(new JobCompletedEvent("tech_dedup", "success", 1, null));

        verify(notificationService, never()).save(any());
    }

    @Test
    void onCrawlerCompleted_notifiesEveryAdminWithSuccessRatio() {
        User admin = admin("admin@example.com");
        when(userRepository.findAdmins()).thenReturn(Flux.just(admin));
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(cmsService.create(any(), any(), any(), any())).thenReturn(Mono.just(CmsContent.builder().build()));

        notifier.onCrawlerCompleted(new CrawlerCompletedEvent(6, 8));

        verify(notificationService).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo("ADMIN_CRAWL_DONE");
        assertThat(saved.getTitle()).isEqualTo("Crawler đã hoàn tất: 6/8 nguồn thành công");
        assertThat(saved.getUserId()).isEqualTo(admin.getId());
        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    void onCrawlerCompleted_writesCmsRow_analyzedWhenPartialSuccess_publishedWhenAllSucceeded() {
        when(userRepository.findAdmins()).thenReturn(Flux.empty());
        when(cmsService.create(any(), any(), any(), any())).thenReturn(Mono.just(CmsContent.builder().build()));

        notifier.onCrawlerCompleted(new CrawlerCompletedEvent(6, 8));
        verify(cmsService).create("Crawler đã hoàn tất: 6/8 nguồn thành công", "Job", LocalDate.now(), "Analyzed");

        notifier.onCrawlerCompleted(new CrawlerCompletedEvent(8, 8));
        verify(cmsService).create("Crawler đã hoàn tất: 8/8 nguồn thành công", "Job", LocalDate.now(), "Published");
    }

    @Test
    void onClusteringCompleted_notifiesEveryAdmin_whenStatusIsSuccess() {
        User admin = admin("admin@example.com");
        when(userRepository.findAdmins()).thenReturn(Flux.just(admin));
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        notifier.onClusteringCompleted(new ClusteringCompletedEvent("success", 184, "2026-07-24-0000", null));

        verify(notificationService).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo("ADMIN_CLUSTERING_DONE");
        assertThat(saved.getTitle()).isEqualTo("Huấn luyện lại cụm công nghệ thành công");
        assertThat(saved.getBody()).isEqualTo("Hoàn tất trong 184s.");
        assertThat(saved.getLink()).isEqualTo("/admin/automation");
        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    void onClusteringCompleted_notifiesEveryAdminWithError_whenStatusIsFailed() {
        User admin = admin("admin@example.com");
        when(userRepository.findAdmins()).thenReturn(Flux.just(admin));
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        notifier.onClusteringCompleted(new ClusteringCompletedEvent("failed", 12, null, "stage_03_train exit 1"));

        verify(notificationService).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo("ADMIN_CLUSTERING_FAILED");
        assertThat(saved.getTitle()).isEqualTo("Huấn luyện lại cụm công nghệ thất bại");
        assertThat(saved.getBody()).isEqualTo("Lỗi: stage_03_train exit 1");
        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }
}
