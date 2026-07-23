package com.techpulse.techradar.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.test.StepVerifier;

import java.util.List;

class SecurityUtilsTest {

    @Test
    void currentUserId_resolvesToAuthenticationName_whenAuthenticated() {
        var authentication = new TestingAuthenticationToken("user-1", null, List.of());

        StepVerifier.create(SecurityUtils.currentUserId()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .expectNext("user-1")
                .verifyComplete();
    }

    @Test
    void currentUserId_completesEmpty_whenNoSecurityContextInReactorContext() {
        StepVerifier.create(SecurityUtils.currentUserId()).verifyComplete();
    }

    @Test
    void currentUserId_completesEmpty_whenAuthenticationIsNotAuthenticated() {
        var authentication = new TestingAuthenticationToken("user-1", null);
        authentication.setAuthenticated(false);

        StepVerifier.create(SecurityUtils.currentUserId()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .verifyComplete();
    }
}
