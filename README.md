# DevOps Study - Zipkin 分支

这个 `feature/zipkin` branch 是在 `master` 基线分支的基础上，增加 Zipkin 分布式链路追踪能力。

`master` 只在各个 service 的日志里显示 `traceId`、`spanId`、`parentSpanId`。这个 branch 进一步把这些 trace/span 数据通过 Spring Boot / Micrometer Tracing 配置导出到 Zipkin，让我们可以在浏览器里看到完整调用链。

## 这个 Branch 添加了什么

新增组件：

- Zipkin：分布式链路追踪 UI 和 trace 存储。

主要配置文件：

- Zipkin Docker Compose：[support/zipkin.yaml](support/zipkin.yaml)
- GatewayService tracing 配置：[GatewayService/src/main/resources/application.yml](GatewayService/src/main/resources/application.yml)
- OrderService tracing 配置：[OrderService/src/main/resources/application.yml](OrderService/src/main/resources/application.yml)
- PaymentService tracing 配置：[PaymentService/src/main/resources/application.yml](PaymentService/src/main/resources/application.yml)
- BankService tracing 配置：[BankService/src/main/resources/application.yml](BankService/src/main/resources/application.yml)

各个 microservice 都通过自己的 `application.yml` 连接到 Zipkin，例如：

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
    export:
      zipkin:
        endpoint: http://localhost:9411/api/v2/spans
```

这里的意思是：

- `sampling.probability: 1.0`：学习环境下采样 100% 请求。
- `endpoint: http://localhost:9411/api/v2/spans`：把 trace/span 数据发送到本机 Zipkin。

## 架构设计

服务调用链路仍然和 `master` 一样：

```text
Postman / Browser
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

Zipkin 链路是旁路观测能力：

```text
GatewayService  ----\
OrderService     ----> Zipkin
PaymentService  ----/
BankService     ---/
```

也就是说，业务请求仍然是 service 之间的 HTTP direct call；Zipkin 只是接收各个 service 自动导出的 tracing 数据。

## 怎么启动 Zipkin

在项目根目录执行：

```bash
docker compose -f support/zipkin.yaml up -d
```

启动后，Zipkin Web UI 地址是：

```text
http://localhost:9411
```

如果需要停止 Zipkin：

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

启动 Zipkin 和四个 microservice 后，在 Postman 里调用 GatewayService 的 request。

请求会经过完整链路：

```text
GatewayService -> OrderService -> PaymentService -> BankService
```

每一层 service 都会把自己的 span 导出到 Zipkin。

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

这表示一次从 GatewayService 发起的请求，已经被 Zipkin 还原成完整的分布式调用链。

## 注意

这个 branch 的重点是：通过 Spring Boot 配置连接 Zipkin，而不是在业务代码里手动创建 Zipkin exporter。

换句话说，Zipkin 是可替换的观测后端。业务代码只负责正常处理请求，tracing export 由 Spring Boot / Micrometer Tracing / OpenTelemetry 相关依赖和 `application.yml` 配置完成。


```txt
总结:

GatewayService 收到没有 tracing header 的外部请求时，Spring Boot/Micrometer 创建新的 traceId 和 root spanId；之后 Gateway、Order、Payment 通过被 ObservationRegistry instrument 的 HTTP 客户端把 trace context 放进请求头传给下游；每个服务收到请求后继续使用同一个 traceId，生成自己的 spanId，并记录 parentId，最后这些 span 被导出到 Zipkin，Zipkin 根据 traceId + spanId + parentId 还原出完整调用链。

从 Gateway 出来后， OrderService - PaymentService - BankService 获得的 request header 都有一个
key = traceparent 的信息，value = 类似 00-df347570f74bc2094ff61917d965d6b3-a3d92b7b94147640-03
其中 00 是 traceparent 协议版本号。
df347570f74bc2094ff61917d965d6b3 = trace_id （所以链里面所有的 trace_id 都一样）
a3d92b7b94147640 = Parant id 就是上一个链里面的 span id
03 其实是二进制的 11, 第一个 1 代表 sampled, 第二个 1 代表 random trace id
```