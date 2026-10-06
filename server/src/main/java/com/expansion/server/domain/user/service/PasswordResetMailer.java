package com.expansion.server.domain.user.service;

import com.expansion.server.global.config.AsyncConfig;
import com.expansion.server.global.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 비밀번호 재설정 메일 비동기 발송 — 요청 스레드 응답 시간과 분리(AsyncConfig 참고). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailer {

    private final MailService mailService;

    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void send(String email, String link, int ttlMinutes) {
        boolean ok = mailService.send(
                email,
                "[PixelPilot] 비밀번호 재설정 안내",
                "비밀번호 재설정을 요청하셨습니다.\n\n"
                        + "아래 링크에서 새 비밀번호를 설정해 주세요 (" + ttlMinutes + "분 동안 1회만 유효):\n"
                        + link + "\n\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다. 비밀번호는 바뀌지 않습니다.");
        if (!ok) log.warn("[MAIL] 비밀번호 재설정 메일 발송 실패 — 사용자는 1분 뒤 다시 요청 가능");
    }
}
