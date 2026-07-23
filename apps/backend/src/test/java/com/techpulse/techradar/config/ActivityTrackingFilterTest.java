package com.techpulse.techradar.config;

import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityTrackingFilterTest {

    @Mock
    private ActivityLogRepository activityLog;

    private ActivityTrackingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ActivityTrackingFilter(activityLog);
        lenient().when(activityLog.recordVisit(any(), any())).thenReturn(Mono.empty());
        lenient().when(activityLog.recordSearch(any())).thenReturn(Mono.empty());
    }

    private WebFilterChain chainRespondingWith(HttpStatus status) {
        return ex -> {
            ex.getResponse().setStatusCode(status);
            return Mono.empty();
        };
    }

    @Test
    void filter_recordsVisitWithAuthenticatedUserId_forASuccessfulRequest() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/companies"));
        var authentication = new TestingAuthenticationToken("user-1", null, List.of());

        StepVerifier.create(filter.filter(exchange, chainRespondingWith(HttpStatus.OK))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .verifyComplete();

        verify(activityLog).recordVisit("user-1", "/companies");
    }

    @Test
    void filter_recordsVisitWithNullUserId_whenAnonymous() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/companies"));

        StepVerifier.create(filter.filter(exchange, chainRespondingWith(HttpStatus.OK))).verifyComplete();

        verify(activityLog).recordVisit(null, "/companies");
    }

    @Test
    void filter_doesNotRecordVisit_forANon2xxResponse() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/companies"));

        StepVerifier.create(filter.filter(exchange, chainRespondingWith(HttpStatus.NOT_FOUND))).verifyComplete();

        verify(activityLog, never()).recordVisit(any(), any());
    }

    @Test
    void filter_skipsEntirely_forOptionsRequests() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.options("/companies"));

        StepVerifier.create(filter.filter(exchange, chainRespondingWith(HttpStatus.OK))).verifyComplete();

        verify(activityLog, never()).recordVisit(any(), any());
    }

    @Test
    void filter_skipsEntirely_forIgnoredPathPrefixes() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health"));

        StepVerifier.create(filter.filter(exchange, chainRespondingWith(HttpStatus.OK))).verifyComplete();

        verify(activityLog, never()).recordVisit(any(), any());
    }

    @Test
    void filter_skipsAdminDashboardPolling() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/admin/dashboard/live-metrics/stream"));

        StepVerifier.create(filter.filter(exchange, chainRespondingWith(HttpStatus.OK))).verifyComplete();

        verify(activityLog, never()).recordVisit(any(), any());
    }

    @Test
    void filter_recordsEachKeyword_forASearchPath() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/radar/search?keywords=java&keywords=golang"));

        StepVerifier.create(filter.filter(exchange, chainRespondingWith(HttpStatus.OK))).verifyComplete();

        verify(activityLog).recordSearch("java");
        verify(activityLog).recordSearch("golang");
    }

    @Test
    void filter_doesNotRecordSearch_forANonSearchPath() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/companies?keywords=java"));

        StepVerifier.create(filter.filter(exchange, chainRespondingWith(HttpStatus.OK))).verifyComplete();

        verify(activityLog, never()).recordSearch(any());
    }

    @Test
    void filter_recordsSearch_forCompareSearchAndGraphExplorePaths() {
        var exchangeCompare = MockServerWebExchange.from(MockServerHttpRequest.get("/compare/search?keywords=rust"));
        StepVerifier.create(filter.filter(exchangeCompare, chainRespondingWith(HttpStatus.OK))).verifyComplete();
        verify(activityLog).recordSearch("rust");

        var exchangeGraph = MockServerWebExchange.from(MockServerHttpRequest.get("/graph/explore?keywords=kotlin"));
        StepVerifier.create(filter.filter(exchangeGraph, chainRespondingWith(HttpStatus.OK))).verifyComplete();
        verify(activityLog).recordSearch("kotlin");
    }
}
