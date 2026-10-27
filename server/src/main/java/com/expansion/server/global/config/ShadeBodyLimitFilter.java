package com.expansion.server.global.config;

import com.expansion.server.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 명암 제안(POST /api/ai/shade-suggest)만 요청 본문 상한 300KB — 큰 그림 폭탄 방어 첫 겹.
 *
 * <p>스프링은 JSON 본문에 기본 상한이 없어 거대한 본문을 통째로 메모리에 읽은 뒤에야 DTO 검사를 한다.
 * Content-Length가 크면 읽기 전에, 없거나 거짓이면(조각 전송) 읽는 도중 넘는 순간 413.
 * 컴포넌트 필터라 Spring Security 체인 뒤에서 돈다 → 비로그인 요청은 본문을 읽지 않고 401.
 */
@Component
public class ShadeBodyLimitFilter extends OncePerRequestFilter {

    static final String PATH = "/api/ai/shade-suggest";
    static final int MAX_BODY = 300_000;   // 64×64 PNG base64는 수 KB

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY) {
            tooLarge(response);
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = request.getInputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
                if (buf.size() > MAX_BODY) {
                    tooLarge(response);
                    return;
                }
            }
        }
        chain.doFilter(new CachedBodyRequest(request, buf.toByteArray()), response);
    }

    private void tooLarge(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.SHADE_BODY_TOO_LARGE;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Connection", "close");
        response.getWriter().write("{\"success\":false,\"message\":\"" + code.getMessage() + "\"}");
    }

    /** 이미 읽은 본문(상한 이하)을 다시 읽을 수 있게 돌려주는 요청 */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
