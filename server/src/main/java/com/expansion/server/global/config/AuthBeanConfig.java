package com.expansion.server.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 인증 공용 빈(PasswordEncoder 등) 정의.
 * SecurityConfig에서 분리한 이유: SecurityConfig가 OAuth2SuccessHandler→AuthService를 참조하고,
 * AuthService가 PasswordEncoder를 주입받아 순환 참조가 발생하기 때문. 빈을 별도 config로 빼서 고리를 끊는다.
 */
@Configuration
public class AuthBeanConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
