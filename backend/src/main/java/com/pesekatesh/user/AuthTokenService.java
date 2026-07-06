package com.pesekatesh.user;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Regjistër në memorie token -> userId (njësoj si RoomManager për dhomat e lojës).
 * Thjeshtë me qëllim: pa Spring Security/JWT — mjafton për "username/password + histori
 * personale", por token-at humbasin nëse backend-i rindizet (loguohu sërish pas restart).
 */
@Service
public class AuthTokenService {

    private final Map<String, Long> tokenToUserId = new ConcurrentHashMap<>();

    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        tokenToUserId.put(token, userId);
        return token;
    }

    public Optional<Long> resolveUserId(String token) {
        if (token == null) return Optional.empty();
        return Optional.ofNullable(tokenToUserId.get(token));
    }

    public void revoke(String token) {
        if (token != null) tokenToUserId.remove(token);
    }
}
