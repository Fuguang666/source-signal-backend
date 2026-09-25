package com.sourcesignal.common;

import lombok.Getter;

/**
 * 业务状态码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 用户相关 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "邮箱已被注册"),
    USER_PASSWORD_ERROR(1003, "密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),

    // 订阅相关 2xxx
    SUBSCRIPTION_NOT_FOUND(2001, "订阅信息不存在"),
    SUBSCRIPTION_EXPIRED(2002, "订阅已到期"),
    TRIAL_LIMIT_REACHED(2003, "今日样例线索已达上限"),

    // 线索相关 3xxx
    LEAD_NOT_FOUND(3001, "线索不存在"),

    // 推送相关 4xxx
    PUSH_CHANNEL_LOCKED(4001, "该推送渠道需升级付费版解锁"),
    PUSH_CONFIG_NOT_FOUND(4002, "推送配置不存在");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
