-- ============================================================
-- SourceSignal 采购信号 SaaS 数据库建表脚本
-- 数据库: sourcesignal
-- 字符集: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS sourcesignal DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sourcesignal;

-- -----------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64) NOT NULL COMMENT '用户名（唯一）',
    password        VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
    email           VARCHAR(128) DEFAULT NULL COMMENT '邮箱（可选）',
    company         VARCHAR(128) DEFAULT NULL COMMENT '公司名称',
    enabled         BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    last_login_at   DATETIME(6) DEFAULT NULL COMMENT '最后登录时间',
    created_at      DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at      DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------
-- 2. 订阅表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscription (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    plan_type           ENUM('TRIAL','PAID') NOT NULL DEFAULT 'TRIAL' COMMENT '套餐类型',
    status              ENUM('TRIAL','ACTIVE','EXPIRED') NOT NULL DEFAULT 'TRIAL' COMMENT '订阅状态',
    trial_start_date    DATE DEFAULT NULL COMMENT '试用开始日期',
    trial_end_date      DATE DEFAULT NULL COMMENT '试用结束日期',
    paid_start_date     DATE DEFAULT NULL COMMENT '付费开始日期',
    paid_end_date       DATE DEFAULT NULL COMMENT '付费结束日期',
    billing_cycle       VARCHAR(16) DEFAULT NULL COMMENT '计费周期（MONTHLY/YEARLY）',
    today_sample_count  INT NOT NULL DEFAULT 0 COMMENT '今日已查看样例线索数',
    sample_reset_at     DATETIME(6) DEFAULT NULL COMMENT '样例计数重置时间',
    created_at          DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at          DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅表';

-- -----------------------------------------------------------
-- 3. 用户订阅配置表（监控品类/地区/关键词）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_subscription_config (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    categories  VARCHAR(512) DEFAULT NULL COMMENT '监控品类（JSON数组）',
    regions     VARCHAR(256) DEFAULT NULL COMMENT '目标地区（JSON数组）',
    keywords    VARCHAR(1024) DEFAULT NULL COMMENT '自定义关键词（JSON数组）',
    created_at  DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at  DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅配置表';

-- -----------------------------------------------------------
-- 4. 采购线索表（Reddit采集 + AI打标）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS procurement_lead (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    external_id     VARCHAR(64) NOT NULL COMMENT 'Reddit帖子唯一ID（去重用）',
    subreddit       VARCHAR(64) NOT NULL COMMENT '来源subreddit',
    title           VARCHAR(512) NOT NULL COMMENT '帖子标题',
    body            TEXT DEFAULT NULL COMMENT '帖子正文',
    source_url      VARCHAR(512) DEFAULT NULL COMMENT '原文链接',
    author          VARCHAR(128) DEFAULT NULL COMMENT '作者ID',
    author_karma    INT DEFAULT NULL COMMENT '作者Karma（去噪用）',
    grade           ENUM('S','A','B') NOT NULL COMMENT '意向等级 S/A/B',
    category        VARCHAR(64) DEFAULT NULL COMMENT '品类（如：宠物用品、3C数码）',
    order_scale     ENUM('SMALL','MEDIUM','LARGE','UNKNOWN') DEFAULT NULL COMMENT '订单量级',
    need_type       ENUM('FULL_AGENT','FACTORY','QC','LOGISTICS','SUPPLY_CHAIN','CONSIDERING_AGENT','BEGINNER') DEFAULT NULL COMMENT '需求类型',
    region          ENUM('NORTH_AMERICA','EUROPE','SOUTHEAST_ASIA','AUSTRALIA','OTHER') DEFAULT NULL COMMENT '目标地区',
    posted_at       DATETIME(6) NOT NULL COMMENT '帖子发布时间',
    collected_at    DATETIME(6) DEFAULT NULL COMMENT '采集入库时间',
    tagged_at       DATETIME(6) DEFAULT NULL COMMENT 'AI打标完成时间',
    reviewed        BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否已人工抽检',
    review_accurate BIT(1) DEFAULT NULL COMMENT '抽检结果是否准确',
    review_note     VARCHAR(512) DEFAULT NULL COMMENT '抽检备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_id (external_id),
    KEY idx_grade (grade),
    KEY idx_category (category),
    KEY idx_region (region),
    KEY idx_posted_at (posted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购线索表';

-- -----------------------------------------------------------
-- 5. 用户-线索关联表（标记/备注/已读）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_lead (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    lead_id     BIGINT NOT NULL COMMENT '线索ID',
    marked      BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否标记为跟进中',
    note        TEXT DEFAULT NULL COMMENT '跟进备注',
    is_read     BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否已读',
    created_at  DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at  DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_lead (user_id, lead_id),
    KEY idx_user_id (user_id),
    KEY idx_marked (marked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-线索关联表';

-- -----------------------------------------------------------
-- 6. 用户推送渠道配置表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_push_channel (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    channel         ENUM('IN_APP','EMAIL','TELEGRAM','WECOM','DINGTALK') NOT NULL COMMENT '推送渠道',
    enabled         BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    frequency       ENUM('REALTIME','DAILY','WEEKLY') DEFAULT NULL COMMENT '推送频率',
    config_json     VARCHAR(1024) DEFAULT NULL COMMENT '渠道配置（JSON）',
    created_at      DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at      DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_channel (user_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户推送渠道配置表';

-- -----------------------------------------------------------
-- 7. 推送记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS push_record (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    lead_id         BIGINT NOT NULL COMMENT '线索ID',
    channel         ENUM('IN_APP','EMAIL','TELEGRAM','WECOM','DINGTALK') NOT NULL COMMENT '推送渠道',
    status          VARCHAR(16) NOT NULL COMMENT '推送状态（SUCCESS/FAILED/PENDING）',
    error_message   VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
    duration_ms     BIGINT DEFAULT NULL COMMENT '推送耗时（毫秒）',
    pushed_at       DATETIME(6) DEFAULT NULL COMMENT '推送时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_lead_id (lead_id),
    KEY idx_channel (channel),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送记录表';

-- -----------------------------------------------------------
-- 修复现有表的 ALTER 语句（如果表已存在但结构不完整）
-- -----------------------------------------------------------
-- 注意：以下语句在表已存在时执行，用于修复结构

-- 修复 user_lead 唯一约束（如果不存在）
-- ALTER TABLE user_lead ADD UNIQUE KEY uk_user_lead (user_id, lead_id);

-- 修复 push_record channel 枚举（添加 IN_APP）
-- ALTER TABLE push_record MODIFY COLUMN channel ENUM('IN_APP','EMAIL','TELEGRAM','WECOM','DINGTALK') NOT NULL;

-- 修复 user_push_channel channel 枚举（添加 IN_APP）
-- ALTER TABLE user_push_channel MODIFY COLUMN channel ENUM('IN_APP','EMAIL','TELEGRAM','WECOM','DINGTALK') NOT NULL;
