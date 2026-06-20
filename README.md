# DevOps Study - Loki / Fluent Bit 日志分支

这个 branch 在原始的四个 Spring Boot 微服务基础上，加入了 Loki、Fluent Bit 和 Grafana，用来学习集中式日志采集与查询。

## 这个 Branch 添加了什么

新增组件：

- Loki：日志存储与查询后端。
- Fluent Bit：日志采集与运输工具，负责读取本地日志文件并推送到 Loki。
- Grafana：日志查询界面，通过 Loki datasource 查询日志。

主要配置文件：

- Fluent Bit 配置：[support/fluent-bit.conf](support/fluent-bit.conf)
- Docker Compose 配置：[support/loki-compose.yaml](support/loki-compose.yaml)
- Grafana Loki datasource 配置：[support/grafana-datasources/loki.yaml](support/grafana-datasources/loki.yaml)
- 各服务 logback 配置：
  - [GatewayService/src/main/resources/logback.xml](GatewayService/src/main/resources/logback.xml)
  - [OrderService/src/main/resources/logback.xml](OrderService/src/main/resources/logback.xml)
  - [PaymentService/src/main/resources/logback.xml](PaymentService/src/main/resources/logback.xml)
  - [BankService/src/main/resources/logback.xml](BankService/src/main/resources/logback.xml)

日志目录：

- 项目根目录下的 [logs](logs) 用来存放运行时日志。
- Git 只保留 [logs/.gitkeep](logs/.gitkeep)，真实 `.log` 文件不会提交。

## 日志是怎么传到 Grafana 的

整体链路如下：

```text
Spring Boot Service
        |
        | logback 写入文件
        v
项目根目录 logs/*.log
        |
        | Fluent Bit tail input 读取日志文件
        v
Fluent Bit
        |
        | Loki output 推送日志，并添加 labels
        v
Loki
        |
        | Grafana Loki datasource 查询
        v
Grafana Explore
```

每个服务通过 `logback.xml` 同时把日志输出到 console 和文件。文件路径统一写到项目根目录的 `logs` 目录，例如：

```text
logs/GatewayService.log
logs/OrderService.log
logs/PaymentService.log
logs/BankService.log
```

Fluent Bit 通过 [support/fluent-bit.conf](support/fluent-bit.conf) 读取这些文件，然后推送到 Loki。推送时会给日志加上 Loki labels，例如：

```ini
Labels      job=devopsstudy,service=OrderService
```

所以在 Grafana 里面可以用 LogQL 查询：

```logql
{job="devopsstudy"}
```

或者只查某一个服务：

```logql
{service="OrderService"}
```

注意：`traceId`、`spanId`、`parentSpanId` 是日志内容里的 MDC 字段；`job`、`service` 是 Fluent Bit 推送到 Loki 时添加的查询标签。

## 怎么启动

先启动 Loki、Grafana 和 Fluent Bit：

```bash
docker compose -f support/loki-compose.yaml up -d
```

然后启动四个微服务：

- GatewayService：`8080`
- OrderService：`8081`
- PaymentService：`8082`
- BankService：`8083`

可以直接在 VS Code 里分别启动四个 Spring Boot application，也可以使用已有的 VS Code task。

启动后，通过 Postman 调用 GatewayService，让请求经过完整链路：

```text
GatewayService -> OrderService -> PaymentService -> BankService
```

服务收到请求后会写日志到 `logs/*.log`，Fluent Bit 会自动读取并推送到 Loki。

## 怎么使用 Grafana 查看日志

打开 Grafana：

```text
http://localhost:3000
```

进入：

```text
Explore -> Loki
```

切换到 `Code` 查询模式，然后执行：

```logql
{job="devopsstudy"}
```

只看 OrderService：

```logql
{service="OrderService"}
```

查包含 traceId 的日志：

```logql
{job="devopsstudy"} |= "traceId="
```

如果知道某一个具体 traceId，可以这样查：

```logql
{job="devopsstudy"} |= "你的 traceId"
```

## Git 日志文件规则

[.gitignore](.gitignore) 会忽略所有真实日志文件：

```gitignore
*.log
logs/
**/logs/
!logs/
!logs/.gitkeep
```

这样仓库会保留 `logs` 目录结构，但不会把运行时日志提交到 Git。
