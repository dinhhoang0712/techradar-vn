package com.techpulse.techradar.features.auth.adapters.output;

import com.techpulse.techradar.features.auth.ports.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Sends password-reset emails via SMTP (JavaMailSender). The actual {@code send} runs on a
 * blocking-friendly scheduler; callers fire-and-forget so SMTP issues never block the request.
 */
@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@techradar.vn}")
    private String from;

    @Value("${app.web.reset-url:http://localhost:5173/login}")
    private String resetUrl;

    @Override
    public Mono<Void> sendPasswordReset(String toEmail, String token) {
        return Mono.fromRunnable(() -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("TechRadar — Đặt lại mật khẩu");
            message.setText(
                    "Bạn (hoặc ai đó) đã yêu cầu đặt lại mật khẩu cho tài khoản TechRadar.\n\n" +
                    "Mã đặt lại: " + token + "\n" +
                    "Mã có hiệu lực trong 60 phút.\n\n" +
                    "Mở " + resetUrl + " và dùng \"Nhập mã đặt lại\" để đổi mật khẩu.\n\n" +
                    "Nếu bạn không yêu cầu, hãy bỏ qua email này.");
            mailSender.send(message);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> sendNotification(String toEmail, String subject, String body) {
        return Mono.fromRunnable(() -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("TechRadar — " + subject);
            message.setText(body);
            mailSender.send(message);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
