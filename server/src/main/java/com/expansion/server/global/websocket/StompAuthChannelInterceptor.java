package com.expansion.server.global.websocket;

import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * STOMP 인바운드 프레임 가로채기.
 * - CONNECT: Authorization(JWT) 검증 후 Principal(userId) 설정
 * - SUBSCRIBE: /topic/commissions/{id} 는 해당 커미션 당사자만 구독 허용
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final CommissionRepository commissionRepository;

    private static final String COMMISSION_TOPIC_PREFIX = "/topic/commissions/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String token = resolveToken(accessor.getFirstNativeHeader("Authorization"));
            if (token == null || !jwtUtil.isValid(token)) {
                throw new MessageDeliveryException("WebSocket 인증에 실패했습니다.");
            }
            accessor.setUser(new StompPrincipal(jwtUtil.getUserId(token)));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            Principal user = accessor.getUser();
            if (user == null) {
                throw new MessageDeliveryException("인증이 필요합니다.");
            }
            Long commissionId = parseCommissionId(accessor.getDestination());
            if (commissionId != null) {
                Long userId = Long.valueOf(user.getName());
                if (!commissionRepository.isParty(commissionId, userId)) {
                    throw new MessageDeliveryException("해당 거래룸에 접근 권한이 없습니다.");
                }
            }
        }

        return message;
    }

    private String resolveToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    // /topic/commissions/{id} → id, 형식이 아니면 null
    private Long parseCommissionId(String destination) {
        if (destination == null || !destination.startsWith(COMMISSION_TOPIC_PREFIX)) return null;
        try {
            return Long.valueOf(destination.substring(COMMISSION_TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
