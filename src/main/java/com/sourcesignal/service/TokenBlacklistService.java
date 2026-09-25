package com.sourcesignal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 黑名单服务（登出用）
 * 后续可替换为 Redis 实现
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    /**
     * 将 token 加入黑名单
     */
    public void blacklist(String token) {
        if (token != null && !token.isEmpty()) {
            blacklist.add(token);
            log.debug("Token 已加入黑名单");
        }
    }

    /**
     * 检查 token 是否在黑名单中
     */
    public boolean isBlacklisted(String token) {
        return token != null && blacklist.contains(token);
    }
}
