package com.expansion.server.global.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 메일 발송 추상화. R2의 r2.enabled 패턴과 동일하게 mail.enabled 플래그로 제어.
 * - mail.enabled=false(로컬 기본): 실제 발송 대신 콘솔 로그(인증 링크 확인용)
 * - mail.enabled=true(프로덕션): Brevo SMTP 릴레이 등으로 발송
 * provider 교체(Brevo→SendGrid→Gmail)는 application.yml의 spring.mail.* 접속 정보만 바꾸면 됨.
 */
@Slf4j
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${mail.enabled:false}")
    private boolean enabled;

    @Value("${mail.from:no-reply@pixelpilot.art}")
    private String from;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("[MAIL disabled] 발송 생략 — to={}, subject={}\n{}", to, subject, body);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.error("[MAIL] mail.enabled=true 이지만 JavaMailSender 미구성 — spring.mail.* 설정 확인 필요. to={}", to);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
            log.info("[MAIL] 발송 완료 — to={}, subject={}", to, subject);
        } catch (Exception e) {
            // 메일 발송 실패가 호출 측 트랜잭션(회원가입 등)을 롤백시키지 않도록 예외를 삼킴.
            // 인증 토큰은 이미 저장되므로 사용자는 '재발송'으로 복구 가능.
            log.error("[MAIL] 발송 실패 — to={}, subject={}, cause={}", to, subject, e.getMessage());
        }
    }
}
