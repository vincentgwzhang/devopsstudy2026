# DevOps Study - Prometheus + OpenTelemetry Collector 分支

这个 `feature/prometheus` branch 是在 `master` 基线分支的基础上，增加 metrics 采集能力，并且把链路改成：

```text
Microservice -> OpenTelemetry Collector -> Prometheus
```

也就是说，Prometheus 不再直接 scrape 四个 Spring Boot service。四个 service 会把 metrics 通过 OTLP 发给 OpenTelemetry Collector，然后 Prometheus 只 scrape Collector 暴露出来的 `/metrics` endpoint。

## 这个 Branch 添加了什么

新增组件：

- Spring Boot Actuator：让每个 service 具备基础 health / metrics 能力。
- Micrometer OTLP Registry：把应用内 Micrometer metrics 通过 OTLP 发给 OpenTelemetry Collector。
- OpenTelemetry Collector：接收四个 service 发来的 OTLP metrics，并转换成 Prometheus 可 scrape 的格式。
- Prometheus：只 scrape OpenTelemetry Collector。

主要配置文件：

- Docker Compose：[support/prometheus.yaml](support/prometheus.yaml)
- Prometheus scrape 配置：[support/prometheus.yml](support/prometheus.yml)
- OpenTelemetry Collector 配置：[support/otel-collector.yaml](support/otel-collector.yaml)
- GatewayService metrics 配置：[GatewayService/src/main/resources/application.yml](GatewayService/src/main/resources/application.yml)
- OrderService metrics 配置：[OrderService/src/main/resources/application.yml](OrderService/src/main/resources/application.yml)
- PaymentService metrics 配置：[PaymentService/src/main/resources/application.yml](PaymentService/src/main/resources/application.yml)
- BankService metrics 配置：[BankService/src/main/resources/application.yml](BankService/src/main/resources/application.yml)

## 和之前直接 Prometheus Scrape 的区别

之前的链路是：

```text
Prometheus -> GatewayService /actuator/prometheus
Prometheus -> OrderService /actuator/prometheus
Prometheus -> PaymentService /actuator/prometheus
Prometheus -> BankService /actuator/prometheus
```

现在的链路是：

```text
GatewayService  ----\
OrderService     ----> OpenTelemetry Collector ----> Prometheus
PaymentService  ----/
BankService     ---/
```

更准确地说：

```text
Microservice --OTLP metrics--> OpenTelemetry Collector --Prometheus format--> Prometheus scrape
```

这个设计的重点是解耦：

- service 不再知道 Prometheus。
- Prometheus 不再知道四个 service。
- Collector 负责接收、处理、转换 metrics。

## 怎么打开 Actuator

每个 microservice 仍然保留 Actuator：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

但是这个 branch 不再使用 `micrometer-registry-prometheus`，而是使用：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-otlp</artifactId>
</dependency>
```

每个 service 的 `application.yml` 暴露基础 endpoint：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

注意：这里不再暴露 `prometheus` endpoint，因为 Prometheus 不再直接 scrape service。

## Service 怎么把 Metrics 发给 Collector

每个 service 的 `application.yml` 都配置了 OTLP metrics export：

```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
  otlp:
    metrics:
      export:
        enabled: true
        url: http://localhost:4318/v1/metrics
        step: 5s
```

这里的意思是：

- `application` tag 会标记 metrics 来自哪个 Spring Boot application。
- `url: http://localhost:4318/v1/metrics` 表示把 metrics 发送到本机 OpenTelemetry Collector。
- `step: 5s` 表示每 5 秒导出一次 metrics，方便本地学习时更快看到结果。

## Collector 怎么暴露给 Prometheus

Collector 配置在 [support/otel-collector.yaml](support/otel-collector.yaml)：

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
  prometheus:
    endpoint: 0.0.0.0:9464

service:
  pipelines:
    metrics:
      receivers:
        - otlp
      processors:
        - batch
      exporters:
        - prometheus
```

这表示 Collector：

1. 在 `4318` 接收 OTLP HTTP metrics。
2. 通过 `batch` processor 批处理。
3. 在 `9464/metrics` 暴露 Prometheus 格式的 metrics。

## Prometheus 怎么连接 Collector

Prometheus 配置在 [support/prometheus.yml](support/prometheus.yml)：

```yaml
scrape_configs:
  - job_name: otel-collector
    metrics_path: /metrics
    static_configs:
      - targets:
          - otel-collector:9464
```

Prometheus 现在只 scrape 一个 target：

```text
otel-collector:9464/metrics
```

它不再直接访问：

```text
host.docker.internal:8080/actuator/prometheus
host.docker.internal:8081/actuator/prometheus
host.docker.internal:8082/actuator/prometheus
host.docker.internal:8083/actuator/prometheus
```

## 怎么启动

先启动 Prometheus 和 OpenTelemetry Collector：

```bash
docker compose -f support/prometheus.yaml up -d
```

这个 compose 会启动：

- `devopsstudy-otel-collector`
- `devopsstudy-prometheus`

暴露端口：

- Prometheus Web UI：`9090`
- Collector OTLP gRPC：`4317`
- Collector OTLP HTTP：`4318`
- Collector Prometheus exporter：`9464`

然后启动四个 Spring Boot application：

- `GatewayService`：`http://localhost:8080`
- `OrderService`：`http://localhost:8081`
- `PaymentService`：`http://localhost:8082`
- `BankService`：`http://localhost:8083`

可以直接在 VS Code 里分别启动这四个 application。

## 怎么使用 Prometheus Web UI

打开浏览器：

```text
http://localhost:9090
```

先检查 scrape target 是否正常：

```text
Status -> Target health
```

现在应该只看到一个核心 target：

```text
otel-collector UP
```

也可以在 Query 页面输入：

```promql
up
```

如果 `otel-collector` 返回 `1`，表示 Prometheus 已经成功 scrape Collector。

## OrderService 自定义 Counter

这个 branch 在 `OrderService` 里保留了自定义 counter：

```java
Counter.builder("order_controller_create")
        .description("Total number of create order endpoint calls")
        .tag("endpoint", "POST /orders")
        .register(meterRegistry);
```

位置：

```text
OrderService/src/main/java/com/observe/order/OrderController.java
```

每次 `POST /orders` 被调用时，这个 counter 都会增加一次。

完整验证流程：

1. 启动 Prometheus 和 OpenTelemetry Collector。
2. 启动四个 microservice。
3. 用 Postman 调用 GatewayService 的 request。
4. 请求会经过 `GatewayService -> OrderService -> PaymentService -> BankService`。
5. 等待几秒钟，让 service 把 metrics 发给 Collector。
6. 在 Prometheus Web UI 查询：

```promql
order_controller_create_total
```

如果看到数值增加，就说明链路已经打通：

```text
OrderService Counter -> OTLP -> Collector -> Prometheus
```

注意：代码里定义的名字是 `order_controller_create`，但 Prometheus 对 counter 类型指标通常会显示为 `_total` 后缀，所以查询名是 `order_controller_create_total`。

## 这个 Branch 的重点

这个 branch 用来学习 metrics 的 OpenTelemetry Collector 架构：

```text
App -> OTLP Metrics -> OpenTelemetry Collector -> Prometheus
```

它和直接使用 Prometheus 的区别是：

- 直接 Prometheus：Prometheus 需要知道每个 service 的地址。
- 加 Collector：service 只知道 Collector，Prometheus 只知道 Collector。

这层 Collector 会带来一点复杂度和一点延迟，但换来的是后端解耦、统一接入和更接近企业环境的 telemetry pipeline。
