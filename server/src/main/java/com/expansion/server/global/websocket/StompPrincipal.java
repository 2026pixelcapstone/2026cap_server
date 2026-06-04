package com.expansion.server.global.websocket;

import java.security.Principal;

// STOMP 세션의 인증 주체 — userId를 name으로 보관
public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(Long userId) {
        this.name = String.valueOf(userId);
    }

    @Override
    public String getName() {
        return name;
    }
}
