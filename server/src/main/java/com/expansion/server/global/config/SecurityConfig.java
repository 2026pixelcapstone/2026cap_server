package com.expansion.server.global.config;

import com.expansion.server.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    // OAuth2SuccessHandler는 소셜 로그인 키 설정 후 활성화
    // private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 비로그인 허용 — 갤러리
                        .requestMatchers(HttpMethod.GET, "/api/gallery/**").permitAll()
                        // 비로그인 허용 — 에셋
                        .requestMatchers(HttpMethod.GET, "/api/assets/**").permitAll()
                        // 비로그인 허용 — 의뢰 게시판
                        .requestMatchers(HttpMethod.GET, "/api/request-posts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/request-posts/{requestPostId}").permitAll()
                        // 비로그인 허용 — 작가 서비스
                        .requestMatchers(HttpMethod.GET, "/api/artist-services").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/artist-services/{serviceId}").permitAll()
                        // 비로그인 허용 — 유저 프로필
                        .requestMatchers(HttpMethod.GET, "/api/users/{userId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/by-nickname/{nickname}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{userId}/followers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{userId}/following").permitAll()
                        // 인증 API
                        .requestMatchers("/api/auth/**").permitAll()
                        // Swagger UI
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // 관리자 전용
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 나머지 로그인 필수
                        .anyRequest().authenticated()
                )
                // OAuth2 소셜 로그인 — Google/Kakao 클라이언트 키 설정 후 활성화
                // .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2SuccessHandler))
                .addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",       // Vite 개발 서버
                "http://192.168.55.229"        // 배포 서버
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
