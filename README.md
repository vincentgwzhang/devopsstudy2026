# DevOps Study - Zipkin + OpenTelemetry Collector 分支

这个 `feature/zipkin` branch 是在 `master` 基线分支的基础上，增加 Zipkin 分布式链路追踪能力，并且把链路改成：

```text
Microservice -> OpenTelemetry Collector -> Zipkin
```

也就是说，四个 Spring Boot service 不再直接连接 Zipkin。它们只把 trace/span 通过 OTLP 发给 OpenTelemetry Collector，然后由 Collector 转发到 Zipkin。

## 这个 Branch 添加了什么

新增组件：

- OpenTelemetry Collector：接收 microservice 发来的 OTLP traces，并转发到 Zipkin。
- Zipkin：分布式链路追踪 UI 和 trace 存储。

主要配置文件：

- Docker Compose：[support/zipkin.yaml](support/zipkin.yaml)
- OpenTelemetry Collector 配置：[support/otel-collector.yaml](support/otel-collector.yaml)
- GatewayService OTLP tracing 配置：[GatewayService/src/main/resources/application.yml](GatewayService/src/main/resources/application.yml)
- OrderService OTLP tracing 配置：[OrderService/src/main/resources/application.yml](OrderService/src/main/resources/application.yml)
- PaymentService OTLP tracing 配置：[PaymentService/src/main/resources/application.yml](PaymentService/src/main/resources/application.yml)
- BankService OTLP tracing 配置：[BankService/src/main/resources/application.yml](BankService/src/main/resources/application.yml)

## 应用怎么连接 OpenTelemetry Collector

每个 microservice 的 `application.yml` 现在连接的是 Collector，不是 Zipkin：

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
    export:
      otlp:
        enabled: true
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces
          transport: http
```

这里的意思是：

- `sampling.probability: 1.0`：学习环境下采样 100% 请求。
- `management.tracing.export.otlp.enabled: true`：启用 OTLP trace export。
- `endpoint: http://localhost:4318/v1/traces`：把 trace/span 发送到本机 OpenTelemetry Collector。
- `transport: http`：使用 OTLP HTTP，而不是 OTLP gRPC。

注意：这里已经没有 `http://localhost:9411/api/v2/spans`。那个是 Zipkin 的地址，现在只应该由 Collector 使用。

## Collector 怎么转发到 Zipkin

Collector 的配置在 [support/otel-collector.yaml](support/otel-collector.yaml)：

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:

exporters:
  zipkin:
    endpoint: http://zipkin:9411/api/v2/spans

service:
  pipelines:
    traces:
      receivers:
        - otlp
      processors:
        - batch
      exporters:
        - zipkin
```

这条配置表达的是：

```text
Collector 接收 OTLP traces
        |
        v
batch processor 做批量处理
        |
        v
zipkin exporter 发给 Zipkin
```

## 整体架构

业务调用链路：

```text
Postman
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

观测数据链路：

```text
GatewayService  ----\
OrderService     ----> OpenTelemetry Collector ----> Zipkin
PaymentService  ----/
BankService     ---/
```

这就是 OpenTelemetry Collector 的价值：service 只知道 OTLP 标准协议，不需要知道后端到底是 Zipkin、Tempo、Jaeger，还是其他 observability 平台。

## 怎么启动 Zipkin 和 Collector

在项目根目录执行：

```bash
docker compose -f support/zipkin.yaml up -d
```

这个 compose 会同时启动：

- `devopsstudy-zipkin`
- `devopsstudy-otel-collector`

暴露端口：

- Zipkin Web UI：`9411`
- Collector OTLP gRPC：`4317`
- Collector OTLP HTTP：`4318`

Zipkin Web UI 地址：

```text
http://localhost:9411
```

停止：

```bash
docker compose -f support/zipkin.yaml down
```

## 怎么启动四个 Microservice

启动四个 Spring Boot application：

- `GatewayService`：`http://localhost:8080`
- `OrderService`：`http://localhost:8081`
- `PaymentService`：`http://localhost:8082`
- `BankService`：`http://localhost:8083`

可以直接在 VS Code 里分别启动这四个 application。

## 怎么产生 Trace

项目提供了 Postman collection：

```text
postman/devopsstudy.postman_collection.json
```

启动 Zipkin、OpenTelemetry Collector 和四个 microservice 后，在 Postman 里调用 GatewayService 的 request。

请求会经过完整链路：

```text
GatewayService -> OrderService -> PaymentService -> BankService
```

每一层 service 都会把自己的 span 发给 Collector，再由 Collector 转发到 Zipkin。

## 怎么在 Zipkin 里查看

打开浏览器：

```text
http://localhost:9411
```

进入 Zipkin 后：

1. 点击 `Find a trace`。
2. 可以不选择 service，直接点击 `RUN QUERY`。
3. 如果 trace 已经上报成功，会看到一条新的调用链结果。
4. 点击结果右侧的 `SHOW`，进入 trace 详情页。

在 trace 详情页里，应该能看到类似这样的调用层级：

```text
gatewayservice
  orderservice
    paymentservice
      bankservice
```

这表示一次从 GatewayService 发起的请求，已经通过 OpenTelemetry Collector 转发到 Zipkin，并被 Zipkin 还原成完整的分布式调用链。

## 这个 Branch 的重点

这个 branch 的重点不是“直接用 Zipkin”，而是理解企业里更常见的解耦方式：

```text
App -> OTLP -> OpenTelemetry Collector -> Trace Backend


OTEL 能转发到多个channel, 如:

Zipkin
Jaeger
Grafana Tempo
Elastic APM / Elasticsearch
Datadog
New Relic
Honeycomb
Dynatrace
AWS X-Ray
Google Cloud Trace
Azure Monitor
Splunk Observability
Prometheus / remote write, 主要是 metrics
Loki, 主要是 logs
Kafka, 作为中间管道
OTLP-compatible backend
```

在这个 branch 里，Trace Backend 是 Zipkin。以后如果要换成 Tempo 或 Jaeger，理论上应该主要修改 Collector 配置，而不是每个 microservice 的业务代码。

现在这个branch 支持  

Jaeger: http://localhost:16686
Zipkin: http://localhost:9411