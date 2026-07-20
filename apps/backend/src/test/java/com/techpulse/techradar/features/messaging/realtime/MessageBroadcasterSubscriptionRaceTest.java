package com.techpulse.techradar.features.messaging.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import org.junit.jupiter.api.RepeatedTest;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the subscribe()/doFinally() cleanup race: an old subscription closing (e.g.
 * a tab being refreshed) used to run its {@code channels.remove(userId, channel)} cleanup
 * concurrently with a brand-new subscribe() for the same user (e.g. the replacement tab opening at
 * the same instant), and could win that race after the new subscriber had already been wired up —
 * silently orphaning the new subscriber from all future {@link MessageBroadcaster#publish} calls
 * until it reconnected.
 * <p>
 * Unlike {@link MessageBroadcasterRedisCrossInstanceTest}, subscribe() and its doFinally() cleanup
 * never touch Redis — they only mutate the local {@code channels} map — so this runs
 * unconditionally, with the Redis-facing collaborators left {@code null}.
 */
class MessageBroadcasterSubscriptionRaceTest {

    private final MessageBroadcaster broadcaster = new MessageBroadcaster(null, null, new ObjectMapper());

    @RepeatedTest(200)
    void concurrentUnsubscribeAndResubscribe_neverOrphansTheNewSubscriber() throws Exception {
        String userId = "race-user-" + System.nanoTime();

        // "Tab 1": already open.
        Disposable oldSubscription = broadcaster.subscribe(userId).subscribe(msg -> { });

        List<DirectMessage> receivedByNewTab = new CopyOnWriteArrayList<>();
        CyclicBarrier barrier = new CyclicBarrier(2);

        // Races doFinally()'s cleanup of "tab 1" against subscribe() wiring up "tab 2" for the
        // very same userId, both released at the same instant.
        Thread closeOldTab = new Thread(() -> {
            awaitBarrier(barrier);
            oldSubscription.dispose();
        });
        Thread openNewTab = new Thread(() -> {
            awaitBarrier(barrier);
            Flux<DirectMessage> newStream = broadcaster.subscribe(userId);
            newStream.subscribe(receivedByNewTab::add);
        });

        closeOldTab.start();
        openNewTab.start();
        closeOldTab.join();
        openNewTab.join();

        DirectMessage message = new DirectMessage(
                "msg-1", "conv-1", "sender-1", "hello", LocalDateTime.of(2026, 7, 20, 12, 0), false);
        // Simulates the Redis-relayed delivery path (subscribeToRedis()'s callback) without
        // actually touching Redis.
        deliverLocally(broadcaster, userId, message);

        assertThat(receivedByNewTab)
                .as("the new tab's subscription must still be wired into the live channels map "
                        + "after racing the old tab's close")
                .containsExactly(message);
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void deliverLocally(MessageBroadcaster broadcaster, String userId, DirectMessage message) throws Exception {
        Method method = MessageBroadcaster.class.getDeclaredMethod("deliverLocally", String.class, DirectMessage.class);
        method.setAccessible(true);
        method.invoke(broadcaster, userId, message);
    }
}
