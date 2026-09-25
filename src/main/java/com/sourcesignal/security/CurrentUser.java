package com.sourcesignal.security;

import com.sourcesignal.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户工具类
 */
@Component
public class CurrentUser {

    /** 获取当前登录用户 */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        return null;
    }

    /** 获取当前用户 ID */
    public Long getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /** 获取当前用户名 */
    public String getCurrentUsername() {
        User user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    /** 获取当前用户邮箱（可选字段） */
    public String getCurrentEmail() {
        User user = getCurrentUser();
        return user != null ? user.getEmail() : null;
    }
}
