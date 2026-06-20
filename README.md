# DevOps Study - Master 基线分支

这个 `master` branch 是整个项目的基础版本。

它的目标很简单：只演示四个 Spring Boot microservice 之间的 HTTP direct call，以及最基础的 `traceId`、`spanId`、`parentSpanId` 日志传播。

## 这个 Branch 有什么

当前包含五个 Maven module：

- `CommonModule`：公共 tracing / MDC 工具类。
- `GatewayService`：入口服务，只负责转发请求到 `OrderService`。
- `OrderService`：订单服务，收到请求后调用 `PaymentService`。
- `PaymentService`：支付服务，收到请求后调用 `BankService`。
- `BankService`：银行服务，作为调用链最后一层。

调用链路：

```text
Client / Postman
        |
        v
GatewayService
        |
        v
OrderService
        |
        v
PaymentService
        |
        v
BankService
```

每个服务通过 HTTP 直接调用下一层服务，没有使用 Spring Security，也没有使用服务注册中心。

## 这个 Branch 没有什么

这个 `master` branch 故意不接入企业级 DevOps / Observability 组件。

当前没有：

- Zipkin
- Prometheus
- Grafana
- Loki
- Fluent Bit
- ELK / Logstash
- OpenTelemetry Collector
- Kubernetes
- Service Mesh

也就是说，这个 branch 只保留最原始、最容易理解的日志 trace 基础能力。

如果要学习某一个企业级 DevOps 组件，请切换到对应的 feature branch 查看详细配置和实现。

## Trace / Span 日志能力

这个 branch 会在日志里显示：

```text
traceId
spanId
parentSpanId
```

日志格式在每个 service 的 `logback.xml` 里配置，例如：

- `GatewayService/src/main/resources/logback.xml`
- `OrderService/src/main/resources/logback.xml`
- `PaymentService/src/main/resources/logback.xml`
- `BankService/src/main/resources/logback.xml`

日志中的 MDC 字段来自 `CommonModule`：

- `CommonModule/src/main/java/com/observe/common/tracing/MdcKeys.java`
- `CommonModule/src/main/java/com/observe/common/tracing/MdcSupport.java`

## 怎么启动

先编译整个项目：

```bash
mvn -q -DskipTests compile
```

然后启动四个服务：

- `GatewayService`：`http://localhost:8080`
- `OrderService`：`http://localhost:8081`
- `PaymentService`：`http://localhost:8082`
- `BankService`：`http://localhost:8083`

可以直接在 VS Code 里启动四个 Spring Boot application。

## 怎么测试

项目提供了 Postman collection：

```text
postman/devopsstudy.postman_collection.json
```

启动四个服务后，通过 Postman 调用 GatewayService，请求会经过完整链路：

```text
GatewayService -> OrderService -> PaymentService -> BankService
```

然后在各个 service 的 console log 里观察 `traceId`、`spanId`、`parentSpanId`。

## Branch 学习建议

建议把 `master` 当成最干净的基础版本：

```text
master = 只看 HTTP 调用链 + trace/span 日志
```

后续如果要学习企业常见 DevOps 能力，请切换到对应 branch：

```text
feature/zipkin      = 分布式链路追踪
feature/prometheus  = metrics / 指标采集
feature/loki        = 日志集中采集与查询
```

具体组件如何配置、如何启动、如何查询，请以对应 branch 的 README 和配置文件为准。
