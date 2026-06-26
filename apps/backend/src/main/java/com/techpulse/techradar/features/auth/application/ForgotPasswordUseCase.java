package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.ports.EmailSender;
import com.techpulse.techradar.features.auth.ports.PasswordResetRepository;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Issues a password-reset token for an email and emails it. Always completes (never reveals whether
 * the email exists). The email is sent fire-and-forget so an unavailable SMTP never breaks the call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForgotPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailSender emailSender;

    public Mono<Void> execute(String email) {
        return userRepository.findByEmail(email)
                .flatMap(user -> passwordResetRepository.createToken(user.getId().toString())
                        .doOnNext(token -> {
                            log.info("[password-reset] token for {} = {}", email, token);
                            emailSender.sendPasswordReset(email, token.toString())
                                    .onErrorResume(e -> {
                                        log.warn("[password-reset] email send failed for {}: {}", email, e.toString());
                                        return Mono.empty();
                                    })
                                    .subscribe(); // fire-and-forget; never blocks the response
                        }))
                .then();
    }
}
