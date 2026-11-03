package com.expansion.server.global.response;

import com.expansion.server.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * 필터·보안 처리기처럼 @ExceptionHandler를 타지 않는 곳에서 ApiResponse와 같은 모양의 실패 JSON을 쓴다.
 * 손으로 JSON 문자열을 만들던 4곳(401·403·IP 차단·명암 본문 초과)이 code를 빠뜨리지 않게 한 곳으로 모음.
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"code\":\"" + errorCode.name()
                + "\",\"message\":\"" + escape(errorCode.getMessage()) + "\"}");
    }

    // ErrorCode 문구는 상수지만, 따옴표·역슬래시가 섞여도 JSON이 깨지지 않게
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
