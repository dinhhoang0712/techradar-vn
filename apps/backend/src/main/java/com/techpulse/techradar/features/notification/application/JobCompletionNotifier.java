package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.auth.ports.EmailSender;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.event.CrawlerCompletedEvent;
import com.techpulse.techradar.features.notification.event.JobCompletedEvent;
import com.techpulse.techradar.features.system.application.DataPlatformJobStatusService;
import com.techpulse.techradar.shared.redis.RedisFanout;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Persistent admin notification when a background job actually finishes (data-platform gold jobs
 * over {@code job:completed}, crawler runs over {@code crawler:completed}) — a backstop for the
 * client-side polling in {@code AdminSettings.tsx}: an admin who triggers a job and navigates away
 * still sees the result on their next visit, via the same in-app notification bell/SSE stream
 * already used for user-facing alerts. Notifies EVERY admin, not just whoever triggered the run —
 * no job-trigger endpoint records who that was (a separate, unfixed gap), and job completion is an
 * ops-wide concern regardless. Clustering retrain is deliberately not covered here: ml-clustering
 * (the Python service) has no Redis connectivity at all today, so wiring that up is a bigger,
 * separate change.
 */
@Component
@RequiredArgsConstructor
public class JobCompletionNotifier {

    private static final String JOB_COMPLETED_CHANNEL = "job:completed";
    private static final String CRAWLER_COMPLETED_CHANNEL = "crawler:completed";
    private static final String ADMIN_JOB_LINK = "/admin/automation";
    /** "Last N runs all failed" — including the run that just triggered this handler. */
    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 3;

    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DataPlatformJobStatusService dataPlatformJobStatusService;
    private final EmailSender emailSender;

    @PostConstruct
    void subscribe() {
        RedisFanout.subscribe(redisListenerContainer, objectMapper, JOB_COMPLETED_CHANNEL,
                JobCompletedEvent.class, this::onJobCompleted);
        RedisFanout.subscribe(redisListenerContainer, objectMapper, CRAWLER_COMPLETED_CHANNEL,
                CrawlerCompletedEvent.class, this::onCrawlerCompleted);
    }

    // Package-private (not private) so JobCompletionNotifierTest can exercise the handler logic
    // directly without mocking the low-level Redis receive/subscribe wiring (RedisFanout itself
    // has no dedicated test in this codebase — RadarBroadcasterTest/NotificationServiceTest don't
    // test @PostConstruct subscribe() either, only the logic it wires to).
    void onJobCompleted(JobCompletedEvent event) {
        if ("success".equals(event.getStatus())) {
            String body = event.getRowsAffected() != null ? event.getRowsAffected() + " dòng đã xử lý." : "Hoàn tất.";
            notifyAllAdmins("ADMIN_JOB_DONE", "Job hoàn tất: " + event.getJobName(), body, false);
            return;
        }
        // Only "failed" reaches here — check whether this is part of a run of consecutive
        // failures (including this one) worth escalating beyond a plain in-app notification.
        dataPlatformJobStatusService.findRunHistory(event.getJobName(), CONSECUTIVE_FAILURE_THRESHOLD, 0)
                .collectList()
                .subscribe(recentRuns -> notifyOnFailure(event, recentRuns));
    }

    private void notifyOnFailure(JobCompletedEvent event, List<Map<String, Object>> recentRuns) {
        boolean repeated = recentRuns.size() == CONSECUTIVE_FAILURE_THRESHOLD
                && recentRuns.stream().allMatch(row -> "failed".equals(row.get("status")));
        String type = repeated ? "ADMIN_JOB_REPEATED_FAILURE" : "ADMIN_JOB_FAILED";
        String title = repeated
                ? "Job liên tục thất bại (" + CONSECUTIVE_FAILURE_THRESHOLD + " lần liên tiếp): " + event.getJobName()
                : "Job thất bại: " + event.getJobName();
        String body = "Lỗi: " + (event.getErrorMsg() != null ? event.getErrorMsg() : "không rõ");
        notifyAllAdmins(type, title, body, repeated);
    }

    void onCrawlerCompleted(CrawlerCompletedEvent event) {
        String title = "Crawler đã hoàn tất: " + event.getSuccessCount() + "/" + event.getTotal() + " nguồn thành công";
        notifyAllAdmins("ADMIN_CRAWL_DONE", title, null, false);
    }

    /** {@code alsoEmail}: escalated failures also get a best-effort email, not just in-app. */
    private void notifyAllAdmins(String type, String title, String body, boolean alsoEmail) {
        userRepository.findAdmins()
                .flatMap(admin -> {
                    Mono<Notification> saved = notificationService.save(Notification.builder()
                            .userId(admin.getId())
                            .type(type)
                            .title(title)
                            .body(body)
                            .link(ADMIN_JOB_LINK)
                            .read(false)
                            .build());
                    if (!alsoEmail) {
                        return saved;
                    }
                    return saved.flatMap(n -> emailSender.sendNotification(admin.getEmail(), title, body)
                            .onErrorResume(e -> Mono.empty())
                            .thenReturn(n));
                })
                .subscribe();
    }
}
