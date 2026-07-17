package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Best-effort @mention notifications, shared by post and comment creation. No mention data is
 * ever persisted — {@code mentionedUserIds} is a transient, request-only signal used solely to
 * drive a notification per target at write time (there's no username system in this app to make
 * an @id token durably resolvable later, unlike hashtags).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MentionNotifier {

    /** Callers must validate this cap themselves BEFORE performing any write (see {@link #tooMany}) —
     *  by the time {@link #notify} runs, the post/comment already exists, so failing late here would
     *  leave a persisted post/comment with a "failed" response. This method only ever no-ops on excess. */
    public static final int MAX_MENTIONS = 10;

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public static boolean tooMany(List<String> mentionedUserIds) {
        return mentionedUserIds != null && mentionedUserIds.size() > MAX_MENTIONS;
    }

    /** Best-effort — never errors; a delivery failure or an over-cap list is logged and swallowed. */
    public Mono<Void> notify(UUID actorId, List<String> mentionedUserIds, String contentLabel, String link) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return Mono.empty();
        }

        Set<String> targets = new LinkedHashSet<>(mentionedUserIds);
        targets.remove(actorId.toString());
        if (targets.isEmpty()) {
            return Mono.empty();
        }

        return userRepository.findById(actorId.toString())
                .flatMapMany(actor -> Flux.fromIterable(targets)
                        .flatMap(targetId -> notifyOne(actor.getFullName(), targetId, contentLabel, link)))
                .then()
                .onErrorResume(e -> {
                    log.warn("Could not send mention notifications", e);
                    return Mono.empty();
                });
    }

    private Mono<Void> notifyOne(String actorName, String targetId, String contentLabel, String link) {
        return Mono.defer(() -> userRepository.findById(targetId)
                        .flatMap(target -> notificationService.save(Notification.builder()
                                .userId(UUID.fromString(targetId))
                                .type("POST_MENTION")
                                .title(actorName + " đã nhắc đến bạn trong một " + contentLabel)
                                .link(link)
                                .read(false)
                                .build())))
                .then()
                .onErrorResume(e -> {
                    // Unknown/deleted/malformed user id from the client's picker — skip silently,
                    // don't fail the post/comment the user already composed.
                    log.warn("Skipping mention notification for user {}", targetId, e);
                    return Mono.empty();
                });
    }
}
