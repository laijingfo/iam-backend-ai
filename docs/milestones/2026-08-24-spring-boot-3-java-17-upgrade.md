# Spring Boot 3 / Java 17 升级里程碑

- 日期：2026-08-24
- 项目：iam-backend
- 里程碑状态：阶段性完成，可编译并已完成多轮启动问题修复
- 升级基线：Spring Boot 2.3.12.RELEASE / Java 11
- 当前基线：Spring Boot 3.5.15 / Java 17

## 目标

本次升级用于淘汰已过期的 Spring Boot 2、Spring Framework 5、Spring Security 5 和 Java 11 技术基线，同时整理冗余依赖、适配 Jakarta 命名空间、恢复应用启动，并降低旧制品安全扫描中暴露的已知风险。

## 已完成改动

### 1. 构建与核心框架

- Spring Boot Parent 从 `2.3.12.RELEASE` 升级至 `3.5.15`。
- Java 编译基线从 11 调整为 17，移除重复的 compiler source/target 配置。
- Spring Framework 升级至 Boot 3.5 管理的 6.2.x。
- Spring Security 升级至 Boot 3.5 管理的 6.5.x。
- 嵌入式 Tomcat 从 9.0.46 升级至 10.1.59。

### 2. Jakarta 迁移

- Servlet、Validation、Annotation、Mail 等业务代码由 `javax.*` 迁移至 `jakarta.*`。
- 邮件实现切换为 `org.eclipse.angus:jakarta.mail`。
- 保留 `javax.crypto`、`javax.net.ssl`、`javax.sql` 等仍属于 Java SE 的包，不做错误替换。

### 3. 数据访问

- 删除未实际使用的 `spring-boot-starter-data-jpa`，启动时不再初始化 Hibernate/JPA。
- MyBatis-Plus 切换为 Boot 3 Starter，并升级至 `3.5.16`。
- 补充独立的 `mybatis-plus-jsqlparser`。
- Druid 切换为 `druid-spring-boot-3-starter:1.2.27`。
- 修正实体、Mapper 和 XML 中与新版 MyBatis-Plus、泛型及类型映射相关的兼容问题。

### 4. Spring Security 与 JWT

- 旧式 Spring Security 配置迁移为 `SecurityFilterChain`。
- 方法安全迁移为 `@EnableMethodSecurity`。
- 清理不再需要的 `TokenConfigurer`、`RsaProperties` 等旧配置。
- JJWT 从 `0.11.2` 升级至 `0.13.0`，同步调整 Token 创建与解析 API。
- 修正认证入口、拒绝处理器、Filter 和用户缓存等 Jakarta/Security 6 兼容问题。

### 5. Redis 与异步配置

- Redis 属性迁移为 Boot 3 的 `spring.data.redis`。
- `RedisConfig` 直接复用 `RedisProperties`，精简重复的连接与序列化配置。
- `RedissonSentinelConfig` 改为读取 Boot 标准 Sentinel 属性，减少环境配置重复。
- Redis 缓存使用明确的 JSON 序列化策略，并处理旧缓存格式导致的反序列化异常。
- 合并异步线程池配置，删除重复的 `AsyncConfig`。
- 精简上下文透传执行器和请求日志调度逻辑。
- 解决 Redis 配置 Bean 被过早实例化而无法经过全部 BeanPostProcessor 的启动告警。

### 6. JSON 与工具库

- 保留原本使用 Fastjson 的业务路径，升级到 Fastjson 2 兼容包 `2.0.61`，没有强制全部改成 Jackson。
- `ApiHubUtils` 统一到 Fastjson 2 API，消除 Fastjson 1/2 混用。
- Redis、JWT 等原本使用 Jackson 的基础设施路径继续使用 Jackson。
- 删除 `json-lib`，并移除其带来的旧 Commons Collections 依赖链。
- 业务代码中的旧 Commons Collections 工具调用迁移为 Spring/Java 标准能力。

### 7. 主要依赖升级与清理

| 依赖 | 升级前 | 当前 |
|---|---:|---:|
| Springdoc | 1.5.12 | 2.8.14 Boot 3 Starter |
| Redisson | 3.16.0 Starter | 3.52.0 Core |
| MyBatis-Plus | 3.5.3.1 | 3.5.16 |
| Druid | 1.1.21 | 1.2.27 Boot 3 Starter |
| Fastjson | 2.0.19 | 2.0.61 |
| Commons Text | 1.11.0 | 1.15.0 |
| Apache POI | 5.2.5 | 5.4.0 |
| JJWT | 0.11.2 | 0.13.0 |
| Apache HttpClient 4 | 4.4.1 | 4.5.14 |
| Apache HttpCore 4 | 4.4.1 | 4.4.16 |
| AWS SDK S3 1.x | 1.12.13 | 1.12.797（临时保留） |
| Playwright | 1.30.0 | 已移除（功能无调用方） |
| jsoup | 1.17.1 | 1.23.1 |
| Jasypt Starter | 3.0.5 | 4.0.4 |

同时删除了未使用或重复的 JPA、PageHelper、ShedLock、jregex、json-lib、Auth0 Java JWT、显式 spring-test 等依赖。

### 8. OpenSAML / SAML 安全依赖与 Java 17 兼容修复

内部组件 `com.lenovo.saml:lenovo-samlsp:1.0.0` 使用 OpenSAML 3 的二进制 API，并且暂时无法获取可维护的新版，因此本阶段保留 OpenSAML `3.1.0`，避免直接跨代升级破坏二进制兼容。私服发布的 `lenovo-samlsp:1.0.0` POM 没有携带实际依赖描述，所以项目必须继续显式声明其 OpenSAML 模块。

升级过程中曾错误排除 Guava，导致启动出现：

```text
NoClassDefFoundError: com/google/common/base/Function
```

已恢复 OpenSAML 必需的 Guava，并显式使用 `33.6.0-jre`，避免采用 OpenSAML 默认依赖的老旧 Guava 18.0。已确认 Guava JAR 中存在 `com.google.common.base.Function`。

安全扫描还发现了 OpenSAML 3.1.0 依赖链中的旧加密/XML 组件。为避免升级 `lenovo-samlsp`，在保持 OpenSAML API 不变的前提下进行了下列定点覆盖：

| 依赖链旧版本 | 当前处理 | 原因 |
|---|---|---|
| `bcprov-jdk15on:1.51`（以及 Cryptacular 1.1.4 带来的 1.59） | 全部排除，显式引入 `bcprov-jdk18on:1.85.2` | 消除已知漏洞，并避免两个 Provider JAR 同时包含 `org.bouncycastle.*` 类 |
| `xmlsec:2.0.3` | 同坐标直接覆盖为 `2.3.5` | 保持在 2.x 兼容线，避免直接跨到 4.x |
| `cryptacular:1.0` | 同坐标直接覆盖为 `1.1.4` | 修复 `CVE-2020-7226`，不采用 API 变化更大的 1.3.x |
| `velocity:1.7` | 暂时保留 | OpenSAML 3 使用 Velocity 1.x API；2.x 坐标和行为均有变化，且项目不允许用户上传或修改模板 |


#### Java 17 SAML Response 解析修复

真实 ADFS 登录回归暴露出 `lenovo-samlsp:1.0.0` 的 Java 17 模块兼容问题：

```text
IllegalAccessError: com.lenovo.adfs.handler.SamlHandler cannot access
com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl
```

反编译确认旧 `SamlHandler.asDOMDocument()` 直接实例化了 JDK 内部 Xerces 类。Java 17 的 `java.xml` 模块不向普通应用导出该包。没有采用长期依赖 `--add-exports` 的启动参数，也没有修改内部 JAR；项目新增 `SamlResponseParser`，并仅将 `SamlController` 的 Response 解析调用切换到应用侧实现。`lenovo-samlsp` 仍负责 `IDPMetaData` 初始化、ADFS issuer 和验签公钥的加载。

新的解析链路包括：

- 只使用标准 `DocumentBuilderFactory`，禁止 DOCTYPE、外部实体、外部 DTD/Schema 和 XInclude。
- 校验 Response 与 Assertion issuer、成功状态、Assertion 数量和过期时间。
- 直接在原始 Assertion DOM 上使用 ADFS 公钥验签，不再创建 JDK 内部 Xerces 实现。
- 要求签名引用当前 Assertion ID，并启用 JSR-105 secure validation。
- 保留 `itcode`、姓名、邮箱、显示名和 manager 等既有属性输出格式。
- 不再把完整 Base64 SAMLResponse 或完整用户属性写入应用日志。


### 9. 日志与启动噪声

- 删除重复/无实际作用的日志配置类，统一使用 `logback-spring.xml`。
- Jasypt 在 `ServletWebServerInitializedEvent` 后逐个刷新可解密属性源，因此会输出多条缓存刷新 INFO；这是正常行为。
- AWS SDK 1.x 的停止支持公告仅针对 `com.amazonaws.util.VersionInfoUtils` 调整为 `ERROR`，其他 S3 日志不受影响。
- 修复升级后请求日志、异步日志和 Redis 初始化相关告警。

### 10. 登录会话与 Redis 缓存整理

- 登录会话初始 TTL 从 30 分钟调整为 1 小时，普通登录、ADFS 登录和内部 `createToken` 接口统一使用该时长。
- 保留“剩余 30 分钟内发生请求，则续期到 1 小时”的滑动续期策略。
- `USER-LOGIN-DATA` 改为每个用户一个独立 key，并与 token 使用相同 TTL；登录写入改为同步，避免登录响应与首次请求之间的异步竞态。
- ADFS 登录显式刷新用户详情和角色缓存，避免请求阶段通过 `SecurityUtils` 查询不到用户，以及登录未执行角色缓存写入的问题。
- 每次登录重新计算并写入角色缓存，其 TTL 与登录会话统一为 1 小时；token 滑动续期时角色缓存同步续期，不再保留原来固定 1 天的生命周期。
- Redis 会话过期仍返回 HTTP 401，但不额外输出 token 失效日志。

本轮调整修复了重新登录后的两个实际问题：

1. ADFS 登录曾移除用户缓存写入，但 `SecurityUtils.getCurrentUser()` 仍通过 `UserDetailsService` 读取用户缓存，导致业务请求抛出 `UsernameNotFoundException`。现已恢复该数据，并改为同步写入，保证登录成功返回时缓存已经可用。
2. ADFS 登录原先只调用 `getUserDomain()`，而角色重新计算和 Redis 写入位于 `getAdfsUser()`，所以重新登录不会更新 `_currentUserRolesRedis`。现已把角色刷新纳入登录链路。

调整后的 Redis 登录数据结构为：

| Key | 内容 | 初始 TTL | 滑动续期 |
|---|---|---:|---|
| `online-token-{token}` | 在线会话、IP、浏览器、登录时间 | 1 小时 | 是 |
| `USER-LOGIN-DATA:{itcode}` | 当前用户详情和授权信息 | 1 小时 | 是 |
| `{itcode}_currentUserRolesRedis` | 业务角色列表 | 1 小时 | 是 |

旧的 `USER-LOGIN-DATA` 是所有用户共享 TTL 的 Hash，无法实现逐用户生命周期控制。新实现改为每用户独立 key；旧 Hash 不再读取，由原 TTL 自然淘汰。

## 安全扫描改善

Boot 2 旧制品报告中的下列组件已经被替换或移除：

- Tomcat 9.0.46
- Spring Boot 2.3.12.RELEASE
- Spring Framework 5.2.15.RELEASE
- Spring Security Core/Web 5.3.9.RELEASE
- SnakeYAML 1.26
- Commons Collections 3.2.1

旧报告中的 Node.js、V8、zlib、OpenSSL 等条目很可能来自 Playwright 浏览器包。经调用链检查，项目内 `ScreenShotUtil.captureAndUpload(...)` 没有调用方，因此已删除 Playwright Maven 依赖、`ScreenShotUtil`、`browser_path` 配置及未使用 import。若最终制品扫描仍出现这些组件，应继续检查容器基础镜像或部署目录。

安全结论应以新的可执行 JAR 或容器镜像扫描结果为准。版本升级只表示旧版本漏洞不再适用，不表示新制品不存在任何新漏洞。

## 已验证内容

- 使用 Microsoft JDK 17 执行 Maven 编译。
- 共编译 395 个 Java 源文件。
- `mvn -DskipTests compile`：`BUILD SUCCESS`。
- 应用侧 Java 17 SAML Response 解析器已完成编译验证，不再引用 `com.sun.*` JDK 内部类。
- Maven 依赖树确认 Cryptacular 1.1.4 会传递引入 bcprov-jdk15on 1.59，并已通过 exclusion 处理；最终打包仍需再次核对只包含 bcprov-jdk18on 1.85.2。
- Logback XML 已通过 `xmllint` 语法检查。
- Guava `33.6.0-jre` 中已确认包含 OpenSAML 运行时需要的 `Function.class`。
- 应用已越过 Hibernate/JPA、Redis BeanPostProcessor、Redis 反序列化和 OpenSAML Guava 缺类等此前的启动阻塞点。

本次没有完成完整自动化测试、生产环境连通性测试或最终安全扫描，因此这些不属于本里程碑的已验证范围。

## 已知限制与后续事项

### P1：迁移 AWS SDK for Java 2.x

当前 AWS SDK 1.x 已停止支持。不能只隐藏告警后长期保留。

现有实现强制使用 S3 Signature V2，并生成两年有效期的预签名 URL。迁移 SDK 2.x 前需要确认 Lenovo xCloud OSS 是否支持 Signature V4，并决定如何处理 SigV4 最长 7 天的预签名限制。

### P1：替换 OpenSAML 与内部 SAML 组件

OpenSAML 3.1.0 年代较久且已经结束支持，但受内部 `lenovo-samlsp` 二进制兼容约束。本阶段采用应用侧解析器和定点依赖覆盖降低风险，没有把 OpenSAML 强行升级到 5.x。后续应逐步替换剩余的内部元数据初始化/请求生成能力，最终移除 `lenovo-samlsp` 和 OpenSAML 3。

### P2：升级 Spring Boot 补丁版本

本里程碑锁定 Spring Boot 3.5.15。后续应结合内部仓库可用性和最新安全公告升级到更新的 3.5.x 补丁版本，并重新执行回归及安全扫描。

### P2：补充回归测试

建议至少覆盖：

- JWT 登录、刷新、鉴权失败响应。
- SAML 元数据初始化及单点登录。
- SAML Response 正常签名、错误签名、issuer 不匹配、过期、缺少 itcode、多个 Assertion 和含 DOCTYPE/外部实体等拒绝路径。
- Redis 单机/Sentinel、缓存读写和旧缓存兼容。
- PostgreSQL CRUD、分页及关键 Mapper XML。
- 邮件发送（仅在 Lenovo 允许的网络环境中）。
- S3 上传、下载、删除和长效 URL。

## 回滚与排障提示

- 不要重新添加 JPA 来解决 MyBatis 问题；本项目的数据访问基线是 MyBatis-Plus。
- 不要再次排除 OpenSAML 的 Guava，除非同时显式提供兼容版本。
- 不要删除 OpenSAML/Cryptacular 上的 `bcprov-jdk15on` exclusions；否则旧 Provider 会重新进入最终 JAR。
- 不要把 xmlsec 直接跨代升级到 4.x，也不要把 Velocity 1.7 机械替换成 `velocity-engine-core` 2.x；必须先完成 OpenSAML 兼容验证。
- SAML 登录若再次出现 `java.xml does not export com.sun.org.apache.xerces.internal.jaxp`，应检查 Controller 是否回退调用了旧 `SamlHandler.parseSamlResponse()`，不要用永久 `--add-exports` 掩盖。
- Redis 出现 JSON 反序列化错误时，先判断是否为升级前遗留缓存；必要时按明确 key 范围清理，不要直接清空整个 Redis。
- AWS SDK 1.x 的 EOL 日志被隐藏不等于依赖已经迁移。
