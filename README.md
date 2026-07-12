# DevOps Study - ELK Observability Branch

这个分支用于演示传统 ELK 方案下的日志和指标观察。

业务 HTTP endpoint 保持不变，所以原来的 Postman collection 仍然可以直接使用：

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

## 这个 Branch 有什么

当前包含五个 Maven module：

- `CommonModule`：公共 tracing / MDC 工具类，以及 ELK 演示指标 runner。
- `GatewayService`：入口服务，只负责转发请求到 `OrderService`。
- `OrderService`：订单服务，收到请求后调用 `PaymentService`。
- `PaymentService`：支付服务，收到请求后调用 `BankService`。
- `BankService`：银行服务，作为调用链最后一层。

ELK 支持文件：

- `support/elk.yaml`：启动 Elasticsearch、Logstash、Kibana、Metricbeat。
- `support/elk/logstash/pipeline/devopsstudy.conf`：接收应用 JSON 日志并写入不同 index。
- `support/elk/metricbeat/metricbeat.yml`：采集 Docker / system CPU、memory、process 等指标。
- `support/setup.sh`：启动 ELK，并创建 Kibana data views。
- `support/post-setup.sh`：在服务启动后发送 Gateway 请求，并检查 ELK index 是否有数据。
- `support/uninstall.sh`：通过 actuator shutdown 停止正在运行的服务，并删除 ELK 资源。

## 数据怎么进 ELK

应用日志走 Logback TCP appender：

```text
Spring Boot service -> Logback JSON -> Logstash :5000 -> Elasticsearch
```

系统和 Docker 指标走 Metricbeat：

```text
Docker / host metrics -> Metricbeat -> Elasticsearch
```

Kibana 地址：

```text
http://localhost:5601
```

Elasticsearch 地址：

```text
http://localhost:9200
```

Logstash TCP 地址：

```text
localhost:5000
```

## Elasticsearch Indices

Logstash 会按 service 拆分应用日志和演示指标：

```text
devopsstudy-logs-gatewayservice-YYYY.MM.dd
devopsstudy-logs-orderservice-YYYY.MM.dd
devopsstudy-logs-paymentservice-YYYY.MM.dd
devopsstudy-logs-bankservice-YYYY.MM.dd

devopsstudy-metrics-gatewayservice-YYYY.MM.dd
devopsstudy-metrics-orderservice-YYYY.MM.dd
devopsstudy-metrics-paymentservice-YYYY.MM.dd
devopsstudy-metrics-bankservice-YYYY.MM.dd
```

Metricbeat 会写入：

```text
devopsstudy-metricbeat-YYYY.MM.dd
```

`support/setup.sh` 会创建三个 Kibana data views：

- `DevOpsStudy Logs`
- `DevOpsStudy Demo Metrics`
- `DevOpsStudy Metricbeat`

## 演示指标

每个服务启动后都会通过 `ObservabilityDemoRunner` 定时输出结构化指标日志。

这些指标不是为了表达真实业务语义，而是为了让你在 Kibana 里清楚看到常见 metric 类型：

- `counter`：`demo.requests.total`
- `meter`：`demo.requests.rate`
- `timer`：`demo.call.duration`
- `gauge`：`jvm.memory.heap.used`、`jvm.memory.non_heap.used`、`process.cpu.load`
- `histogram`：`demo.call.duration.bucket`

关键字段：

```text
event_dataset = demo.metrics
service_name
metric_type
metric_name
metric_value
metric_unit
traceId
spanId
parentSpanId
```

## 怎么启动

### 1. 准备 ELK

先启动 ELK，并创建 Kibana data views：

```bash
./support/setup.sh
```

脚本结束后会打印下一步提示。

### 2. 编译项目

因为四个服务依赖 `CommonModule`，用 Maven 单独启动某个 service 前建议先执行 `install`：

```bash
mvn -q -DskipTests install
```

### 3. 手动启动四个服务

- `GatewayService`：`http://localhost:8080`
- `OrderService`：`http://localhost:8081`
- `PaymentService`：`http://localhost:8082`
- `BankService`：`http://localhost:8083`

### 4. 发送样本请求并验证 ELK 数据

服务启动后运行：

```bash
./support/post-setup.sh
```

这个脚本会：

- 等待四个服务健康检查 endpoint
- 通过 Gateway 调用 `POST http://localhost:8080/orders`
- 等待 Logstash / Metricbeat 写入 Elasticsearch
- 检查 `devopsstudy-logs-*`
- 检查 `devopsstudy-metrics-*`
- 检查 `devopsstudy-metricbeat-*`

### 5. 清理环境

需要清理时运行：

```bash
./support/uninstall.sh
```

它会尝试调用四个服务的 actuator shutdown endpoint：

```text
POST /actuator/shutdown
```

然后删除 Kibana data views、Elasticsearch indices、ELK containers、Elasticsearch volume 和本地 `support/logs`。

### VS Code Tasks

也可以直接使用 VS Code task：

```text
setup: elk
post-setup: verify
uninstall: elk
```

## 怎么测试

项目提供了 Postman collection：

```text
postman/devopsstudy.postman_collection.json
```

启动四个服务后，调用：

```text
POST http://localhost:8080/orders
```

请求体：

```json
{
  "customerId": "customer-1001",
  "amount": 68.80,
  "currency": "USD"
}
```

或者直接运行 post-setup 脚本。它会发送一次 Gateway 请求，并检查 Elasticsearch 里是否有日志和指标：

```bash
./support/post-setup.sh
```

脚本会检查：

- 四个服务健康检查 endpoint
- Gateway -> Order -> Payment -> Bank 调用链
- Elasticsearch 中是否出现 `devopsstudy-logs-*`
- Elasticsearch 中是否出现 `devopsstudy-metrics-*`
- Elasticsearch 中是否出现 `devopsstudy-metricbeat-*`

## Kibana 里怎么看

打开：

```text
http://localhost:5601
```

建议先看：

- Discover -> `DevOpsStudy Logs`
- Discover -> `DevOpsStudy Demo Metrics`
- Discover -> `DevOpsStudy Metricbeat`

几个好用的筛选条件：

```text
service_name: "OrderService"
event_dataset: "demo.metrics"
metric_type: "gauge"
metric_name: "jvm.memory.heap.used"
traceId: *
```

做 dashboard 时可以用 `DevOpsStudy Demo Metrics` 建 Lens：

- X axis：`@timestamp`
- Break down by：`service_name`
- Filter：`metric_name: "jvm.memory.heap.used"`
- Y axis：`Average(metric_value)` 或 `Max(metric_value)`

Metricbeat 自带 dashboard 也会尝试加载，可以在 Kibana 的 Dashboards 里搜索 `Metricbeat`。
