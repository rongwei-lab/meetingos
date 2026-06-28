# MeetingOS Backend

MeetingOS MVP 后端，采用 Java 17、Spring Boot 3.x、Maven、JPA/Hibernate、Flyway 和 MySQL。TDuck 作为独立表单系统接入，本工程仅保存表单绑定、字段映射、Webhook 同步结果和会议业务主数据。

## 本地运行

```bash
mvn spring-boot:run
```

测试 profile 使用 H2：

```bash
mvn test
```

OpenAPI 地址：

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`

## 初始管理员

后端不再写入固定弱口令账号。首次启动且没有可用超级管理员时，会读取环境变量创建初始管理员：

- `MEETINGOS_INIT_ADMIN_USERNAME`
- `MEETINGOS_INIT_ADMIN_PASSWORD`
- `MEETINGOS_INIT_ADMIN_EMAIL`

`MEETINGOS_INIT_ADMIN_PASSWORD` 必须是至少 12 位的非默认强密码。生产环境还必须替换 JWT secret 和 TDuck Webhook secret。
