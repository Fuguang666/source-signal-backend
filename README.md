# SourceSignal 采购信号

> 订阅制跨境采购线索 SaaS — 实时采集 Reddit 采购需求，AI 打标分级，5 分钟内推送

## 项目简介

SourceSignal 面向跨境采购商、海外电商卖家与外贸服务商，实时监控 Reddit 15–20 个核心采购社区（r/ChinaSourcing、r/Business_China、r/AmazonSeller 等）的公开需求帖，经关键词库初筛与 AI 结构化打标（品类、量级、需求类型、地区）并完成 S/A/B 意向分级，将高意向采购线索在新帖发布 5 分钟内推送至订阅用户。

## 技术栈

| 类别 | 技术选型 |
|------|----------|
| 框架 | Spring Boot 3.2.5 |
| JDK | Java 17+ |
| 构建 | Maven |
| ORM | Spring Data JPA + Hibernate |
| 数据库 | MySQL 8.0（生产）/ H2（开发） |
| 缓存 | Redis |
| 安全 | Spring Security + JWT |
| API 文档 | SpringDoc OpenAPI (Swagger) |
| 工具库 | Hutool、Lombok |
| 邮件 | Spring Boot Mail |
| HTTP 客户端 | WebFlux WebClient |

## 项目结构

```
src/main/java/com/sourcesignal/
├── SourceSignalApplication.java    # 启动类
├── config/                          # 配置类
│   ├── OpenApiConfig.java           # Swagger 配置
│   ├── RedisConfig.java             # Redis 配置
│   ├── SchedulingConfig.java        # 定时/异步配置
│   └── WebClientConfig.java         # HTTP 客户端配置
├── controller/                      # 控制器层（API 接口）
│   ├── AccountController.java       # 账号设置
│   ├── AdminController.java         # 后台管理
│   ├── AuthController.java          # 认证（注册/登录）
│   ├── DashboardController.java     # 仪表盘
│   ├── LeadController.java          # 线索库
│   ├── PushController.java          # 推送与通知
│   └── SubscriptionController.java  # 订阅与套餐
├── service/                         # 业务逻辑层
│   ├── AdminService.java
│   ├── AuthService.java
│   ├── DashboardService.java
│   ├── LeadService.java
│   ├── PushService.java
│   ├── SubscriptionService.java
│   └── UserService.java
├── repository/                      # 数据访问层
│   ├── LeadRepository.java
│   ├── PushRecordRepository.java
│   ├── SubscriptionRepository.java
│   ├── UserLeadRepository.java
│   ├── UserPushChannelRepository.java
│   ├── UserRepository.java
│   └── UserSubscriptionConfigRepository.java
├── entity/                          # JPA 实体
│   ├── Lead.java                    # 采购线索
│   ├── PushRecord.java              # 推送记录
│   ├── Subscription.java            # 订阅
│   ├── User.java                    # 用户
│   ├── UserLead.java                # 用户-线索关联（标记/备注）
│   ├── UserPushChannel.java         # 用户推送渠道配置
│   └── UserSubscriptionConfig.java  # 用户订阅配置（品类/地区/关键词）
├── dto/                             # 数据传输对象
│   ├── AuthResponse.java
│   ├── DashboardDTO.java
│   ├── LeadDTO.java
│   ├── LeadQueryRequest.java
│   ├── LoginRequest.java
│   └── RegisterRequest.java
├── enums/                           # 枚举
│   ├── LeadGrade.java               # S/A/B 意向等级
│   ├── NeedType.java                # 需求类型
│   ├── OrderScale.java              # 订单量级
│   ├── PlanType.java                # 套餐类型
│   ├── PushChannelType.java         # 推送渠道
│   ├── PushFrequency.java           # 推送频率
│   ├── Region.java                  # 目标地区
│   └── SubscriptionStatus.java      # 订阅状态
├── common/                          # 通用类
│   ├── BusinessException.java       # 业务异常
│   ├── GlobalExceptionHandler.java  # 全局异常处理
│   ├── PageResult.java              # 分页响应
│   ├── Result.java                  # 统一响应
│   └── ResultCode.java              # 状态码枚举
├── security/                        # 安全认证
│   ├── CurrentUser.java             # 当前用户工具
│   ├── JwtAuthenticationFilter.java # JWT 过滤器
│   ├── JwtUtil.java                 # JWT 工具
│   └── SecurityConfig.java          # Security 配置
├── collector/                       # 数据采集层
│   └── RedditCollector.java         # Reddit 采集器
├── processor/                       # 智能处理层
│   └── LeadProcessor.java           # 线索处理器（初筛/打标/分级/去噪）
└── scheduler/                       # 定时任务
    └── CollectionScheduler.java     # 采集调度器
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+（生产环境）
- Redis（生产环境）

### 开发环境运行

开发环境默认使用 H2 内存数据库，无需额外配置：

```bash
# 克隆项目
cd SourceSignalProject

# 编译
mvn clean compile

# 运行（dev profile）
mvn spring-boot:run
```

启动后访问：
- API 文档：http://localhost:8080/api/swagger-ui.html
- H2 控制台：http://localhost:8080/api/h2-console

### 生产环境运行

```bash
# 打包
mvn clean package -DskipTests

# 运行（prod profile，需配置环境变量）
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=sourcesignal
export DB_USER=root
export DB_PASSWORD=your_password
export REDIS_HOST=localhost
export JWT_SECRET=your-secret-key

java -jar target/source-signal-1.0.0.jar --spring.profiles.active=prod
```

## API 接口概览

| 模块 | 路径前缀 | 说明 |
|------|----------|------|
| 认证 | `/auth` | 注册、登录 |
| 仪表盘 | `/dashboard` | 首页统计、监控状态 |
| 线索库 | `/leads` | 线索列表、详情、标记、备注 |
| 推送 | `/push` | 渠道管理、频率设置、测试推送 |
| 订阅 | `/subscription` | 订阅信息、套餐、升级、取消 |
| 账号 | `/account` | 个人资料、订阅配置、关键词 |
| 后台 | `/admin` | 用户管理、线索抽检 |

## 核心业务规则

### 套餐模型
- **试用版**：7 天免费，每日 3 条样例线索，2 品类 + 1 地区，7 天历史库
- **付费版**：$129/月（年付 8 折），不限线索量、全品类全地区、多渠道推送、30 天历史库 + CSV 导出

### 线索分级
- **S 级**：明确寻求采购代理/供应商 + 具体品类量级 + 询价
- **A 级**：有明确采购计划，提推荐
- **B 级**：潜在需求，未明确

### 推送时效
- 新帖发布后 5 分钟内完成采集、打标、分级与推送
- 注册后 10 分钟内推送第一条匹配线索

## MVP 功能清单（24 项）

### 数据采集层（P0）
1. Reddit 核心 subreddit 实时监控（15–20 个）
2. 数据 API 采集（分阶段合规）
3. 关键词库规则初筛

### 智能处理层（P0）
4. AI 结构化打标（品类/量级/需求类型/地区）
5. 意向分级（S/A/B）
6. 数据清洗（去重/排除广告帖等）
7. 账号去噪（新号/低 Karma）
8. 人工抽检与 Prompt 迭代

### 用户端后台（P0/P1）
9. 邮箱注册/登录
10. 订阅配置（品类+地区）
11. 线索列表查看
12. 线索标记与备注
13. 历史线索库（7 天）
14. 自定义关键词过滤（P1，付费版）
15. 30 天历史库 + CSV 导出（P1，付费版）

### 推送层（P0）
16. 邮件推送
17. Telegram Bot 推送
18. 企业微信/钉钉机器人推送

### 商业化闭环（P0/P1）
19. 免费试用机制
20. 订阅套餐配置
21. 支付渠道（Stripe + 微信/支付宝）
22. 试用期结束促单（P1）

### 后台管理（P0）
23. 用户与订阅状态管理
24. 线索抽检/审核入口

## 非 MVP 范围（按路线图延后）

- 企业版全套（子账号/API/竞品监控）→ 阶段三（第 7–24 周）
- Quora、外贸论坛等多数据源 → 阶段四（24 周+）
- AI 回复、自动 DM、CRM 对接 → 明确砍掉

## License

Private
