# User Access Review（UAR）后端

User Access Review 是 Lenovo 内部的用户访问评审与权限合规平台。本仓库提供后端服务，覆盖访问评审、风险管理、邮件通知、数据同步、审计日志、报表与 SAML/JWT 认证等能力。

## 当前基线

- Java 17
- Spring Boot 3.5.15
- Spring Framework 6.2.x / Spring Security 6.5.x
- MyBatis-Plus 3.5.16（不使用 Spring Data JPA）
- PostgreSQL + Druid 1.2.27
- Redis + Redisson 3.52.0
- Jakarta Servlet / Validation / Mail API
- Maven 3.6.3+

本项目已于 2026-08-24 完成 Spring Boot 2.3.12、Java 11 到 Spring Boot 3.5.15、Java 17 的阶段性升级。改动范围、验证结果和遗留事项见：[Spring Boot 3 / Java 17 升级里程碑](docs/milestones/2026-08-24-spring-boot-3-java-17-upgrade.md)。

## 主要模块

```text
src/main/java/com/lenovo/
├── config/       Spring、Redis、线程池、过滤器等配置
├── controller/   HTTP 接口
├── entity/       MyBatis-Plus 实体
├── mapper/       Mapper 接口
├── notify/       邮件通知
├── security/     Spring Security、JWT、SAML
├── service/      业务服务
├── strategy/     业务策略
└── util/         S3、JSON、Redis、Excel、截图等工具

src/main/resources/
├── mapper/                   MyBatis XML
├── application.yml          公共配置
├── application-dev.yml      开发环境
├── application-test.yml     测试环境
├── application-uat.yml      UAT 环境
├── application-prod.yml     生产环境
├── application-prod-na.yml  NA 生产环境
└── logback-spring.xml        日志配置
```

## 本地环境

开始前需要准备：

- JDK 17
- Maven 3.6.3 或更高版本
- PostgreSQL
- Redis；UAT/生产环境可使用 Sentinel
- Lenovo Maven 仓库访问权限（项目包含内部依赖）

确认 Java 和 Maven 实际使用 JDK 17：

```bash
java -version
mvn -version
```

## 配置

Maven Profile 与 Spring Profile 的对应关系如下：

| Maven Profile | Spring Profile | 说明 |
|---|---|---|
| `dev` | `dev` | 默认，本地开发 |
| `uat` | `uat` | UAT |
| `prod` | `prod` | 生产 |
| `prod-na` | `prod-na` | NA 生产 |

敏感配置应通过环境变量或部署平台注入，不要提交真实凭据。主要变量包括：

| 环境变量 | 用途 |
|---|---|
| `JDBC_POSTGRESQL`、`JDBC_NAME`、`JDBC_PWD` | PostgreSQL |
| `REDIS_HOST`、`REDIS_PWD`、`REDIS_DATABASE` | Redis 单机/通用配置 |
| `REDIS_MASTER`、`REDIS_NODES` | Redis Sentinel |
| `MAIL_USERNAME`、`MAIL_PASSWORD` | 邮件服务 |
| `AWS_AK`、`AWS_SK`、`OSS_ENDPOINT` | S3 兼容对象存储 |
| `JASYPT_PWD` | Jasypt 解密密码（仅使用 `ENC(...)` 时需要） |

Redis 配置使用 Spring Boot 3 属性前缀：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

## 编译与运行

默认开发环境：

```bash
mvn clean package -DskipTests
java -jar target/iam-0.0.1-SNAPSHOT.jar
```

指定环境构建：

```bash
mvn clean package -Puat -DskipTests
mvn clean package -Pprod -DskipTests
mvn clean package -Pprod-na -DskipTests
```

开发模式运行：

```bash
mvn spring-boot:run -Pdev
```

服务默认端口为 `8088`，context path 为 `/itsc`。

## 关键依赖

| 组件 | 当前版本/来源 |
|---|---|
| Spring Boot | 3.5.15 |
| MyBatis-Plus | 3.5.16 |
| Druid | 1.2.27 |
| Redisson | 3.52.0 |
| Springdoc OpenAPI | 2.8.14 |
| JJWT | 0.13.0 |
| Fastjson 兼容包 | 2.0.61 |
| Apache POI | 5.4.0 |
| Guava | 33.6.0-jre |
| OpenSAML | 3.1.0（待后续专项升级） |
| AWS SDK for Java | 1.12.797（已停止支持，待迁移 2.x） |

## 运维注意事项

- 内部 SMTP 仅在允许的 Lenovo 网络环境中可用；非 Lenovo 网段可能收到连接 EOF 或访问拒绝。
- AWS SDK 1.x 的停止支持公告已单独降到 `ERROR`，其他 AWS/S3 日志仍正常输出；隐藏公告不代表风险已消除。
- 若配置中没有任何 `ENC(...)` 值，可在后续删除 Jasypt 依赖和配置。
- 安全扫描应针对最终可执行 JAR 或容器镜像，不应扫描整个工作目录或 Maven 本地缓存。

## 相关文档

- [Spring Boot 3 / Java 17 升级里程碑](docs/milestones/2026-08-24-spring-boot-3-java-17-upgrade.md)
- [Selector Options API](docs/api/selector-options-api.md)

sys_role_menu表说明
checked为true表示角色有该菜单权限
