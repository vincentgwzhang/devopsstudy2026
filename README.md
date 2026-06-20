# DevOps Study - Prometheus 分支

这个 `feature/prometheus` branch 是在 `master` 基线分支的基础上，增加 Spring Boot Actuator 和 Prometheus metrics 采集能力。

`master` 只演示四个 microservice 的 HTTP direct call 和基础 trace/span 日志。这个 branch 进一步让每个服务暴露 Actuator metrics endpoint，并让 Prometheus 定时读取这些指标。

## 这个 Branch 添加了什么

新增组件：

- Spring Boot Actuator：让每个 service 暴露健康检查和 metrics endpoint。
- Micrometer Prometheus Registry：把 Micrometer metrics 转成 Prometheus 可读取的格式。
- Prometheus：定时 scrape 四个 microservice 的 `/actuator/prometheus` endpoint。

主要配置文件：

- Prometheus Docker Compose：[support/prometheus.yaml](support/prometheus.yaml)
- Prometheus scrape 配置：[support/prometheus.yml](support/prometheus.yml)
- GatewayService Actuator 配置：[GatewayService/src/main/resources/application.yml](GatewayService/src/main/resources/application.yml)
- OrderService Actuator 配置：[OrderService/src/main/resources/application.yml](OrderService/src/main/resources/application.yml)
- PaymentService Actuator 配置：[PaymentService/src/main/resources/application.yml](PaymentService/src/main/resources/application.yml)
- BankService Actuator 配置：[BankService/src/main/resources/application.yml](BankService/src/main/resources/application.yml)

## 怎么打开 Actuator

每个 microservice 都增加了 Actuator 依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

同时增加 Prometheus registry：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

然后在每个 service 的 `application.yml` 里暴露 endpoint：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

启动服务后，可以直接访问：

```text
http://localhost:8080/actuator/health
http://localhost:8080/actuator/prometheus
```

其他服务端口分别是：

- GatewayService：`8080`
- OrderService：`8081`
- PaymentService：`8082`
- BankService：`8083`

## Prometheus 是怎么连接上的

Prometheus 不是被 service 主动调用的。

它的模式是 pull，也就是 Prometheus 定时去每个 service 的 `/actuator/prometheus` endpoint 拉取 metrics。

配置在 [support/prometheus.yml](support/prometheus.yml)：

```yaml
scrape_configs:
  - job_name: gateway-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - host.docker.internal:8080

  - job_name: order-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - host.docker.internal:8081
```

这里使用 `host.docker.internal`，是因为 Prometheus 运行在 Docker container 里面，而四个 Spring Boot service 是在宿主机上运行的。

Docker Compose 里也配置了：

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

这样 Prometheus container 才能访问宿主机上的 `localhost:8080-8083`。

## 怎么启动 Prometheus

先启动四个 microservice：

- `GatewayService`
- `OrderService`
- `PaymentService`
- `BankService`

然后在项目根目录启动 Prometheus：

```bash
docker compose -f support/prometheus.yaml up -d
```

Prometheus Web UI 地址：

```text
http://localhost:9090
```

停止 Prometheus：

```bash
docker compose -f support/prometheus.yaml down
```

## 怎么使用 Prometheus Web UI

打开浏览器：

```text
http://localhost:9090
```

先检查 scrape target 是否正常：

```text
Status -> Target health
```

如果四个服务都正常，应该看到：

```text
gateway-service UP
order-service UP
payment-service UP
bank-service UP
```

也可以在 Query 页面输入：

```promql
up
```

如果返回值是 `1`，表示 Prometheus 能成功 scrape 对应 service。

## OrderService 自定义 Counter

这个 branch 在 `OrderService` 里增加了一个自定义 counter：

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

在 Prometheus 里查询时要使用：

```promql
order_controller_create_total
```

注意：代码里定义的名字是 `order_controller_create`，但是 Prometheus 对 counter 类型指标会自动加上 `_total` 后缀，所以查询名是 `order_controller_create_total`。

完整验证流程：

1. 启动四个 microservice。
2. 启动 Prometheus。
3. 用 Postman 调用 GatewayService 的 request。
4. 请求会经过 `GatewayService -> OrderService -> PaymentService -> BankService`。
5. 在 Prometheus Web UI 查询：

```promql
order_controller_create_total
```

如果看到数值增加，就说明 Prometheus 已经正确读取了 `OrderService` 暴露出来的自定义 metric。

## 这个 Branch 的重点

这个 branch 用来学习 metrics 采集：

```text
Spring Boot Actuator -> /actuator/prometheus -> Prometheus scrape -> Prometheus Web UI 查询
```

它和 Zipkin / Loki 的方向不同：

- Zipkin 关注 trace。
- Prometheus 关注 metrics。
- Loki 关注 logs。

这个 branch 只负责 Prometheus metrics，不负责日志集中化，也不负责 trace 可视化。
