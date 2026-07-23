package com.techpulse.techradar.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFanoutTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ReactiveRedisMessageListenerContainer container;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    private record Ping(String value) {
    }

    @SuppressWarnings("unchecked")
    private ReactiveSubscription.Message<String, String> messageWith(String json) {
        ReactiveSubscription.Message<String, String> message = mock(ReactiveSubscription.Message.class);
        when(message.getMessage()).thenReturn(json);
        return message;
    }

    @Test
    void subscribe_invokesOnEvent_forEachSuccessfullyDeserializedMessage() {
        ReactiveSubscription.Message<String, String> first = messageWith("{\"value\":\"hello\"}");
        ReactiveSubscription.Message<String, String> second = messageWith("{\"value\":\"world\"}");
        when(container.<String, String>receive(any(), any(), any())).thenReturn(Flux.just(first, second));
        List<Ping> received = new ArrayList<>();

        RedisFanout.subscribe(container, objectMapper, "live:test", Ping.class, received::add);

        assertThat(received).containsExactly(new Ping("hello"), new Ping("world"));
    }

    @Test
    void subscribe_skipsMalformedMessages_butStillDeliversSubsequentValidOnes() {
        ReactiveSubscription.Message<String, String> malformed = messageWith("not-valid-json");
        ReactiveSubscription.Message<String, String> valid = messageWith("{\"value\":\"ok\"}");
        when(container.<String, String>receive(any(), any(), any())).thenReturn(Flux.just(malformed, valid));
        List<Ping> received = new ArrayList<>();

        RedisFanout.subscribe(container, objectMapper, "live:test", Ping.class, received::add);

        assertThat(received).containsExactly(new Ping("ok"));
    }

    @Test
    void publish_serializesAndSendsToTheGivenChannel() {
        when(redisTemplate.convertAndSend(eq("live:test"), any())).thenReturn(Mono.just(1L));

        RedisFanout.publish(redisTemplate, objectMapper, "live:test", new Ping("hello"));

        verify(redisTemplate).convertAndSend("live:test", "{\"value\":\"hello\"}");
    }

    @Test
    void publish_doesNotThrow_whenRedisSendFails() {
        when(redisTemplate.convertAndSend(eq("live:test"), any()))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        RedisFanout.publish(redisTemplate, objectMapper, "live:test", new Ping("hello"));
    }
}
