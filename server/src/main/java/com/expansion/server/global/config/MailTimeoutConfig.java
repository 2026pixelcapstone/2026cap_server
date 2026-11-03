package com.expansion.server.global.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * SMTP 접속·읽기·쓰기 시간 제한 기본값.
 * JavaMail 기본값은 '무제한'이라, 메일 서버가 응답하지 않으면 발송 스레드(가입 이벤트는 DB 연결까지)를 계속 붙잡는다.
 * 프로덕션 설정 파일은 레포 밖(/opt/pixelart)이라 yml 예시만으론 적용이 보장되지 않아 코드에서 기본값을 넣는다.
 * spring.mail.properties.mail.smtp.*timeout 으로 따로 지정하면 그 값이 우선(putIfAbsent).
 */
@Configuration
public class MailTimeoutConfig {

    static final String CONNECT_TIMEOUT_MS = "5000";
    static final String IO_TIMEOUT_MS = "10000";

    @Bean
    public static BeanPostProcessor mailTimeoutDefaults() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof JavaMailSenderImpl sender) {
                    Properties props = sender.getJavaMailProperties();
                    props.putIfAbsent("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MS);
                    props.putIfAbsent("mail.smtp.timeout", IO_TIMEOUT_MS);
                    props.putIfAbsent("mail.smtp.writetimeout", IO_TIMEOUT_MS);
                }
                return bean;
            }
        };
    }
}
