package com.observe.common.tracing;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;

public final class ObservabilityDemoRunner implements CommandLineRunner, DisposableBean {

    private final Logger log;
    private final String serviceName;
    private final AtomicLong counter = new AtomicLong();
    private final ScheduledExecutorService scheduler;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();

    public ObservabilityDemoRunner(String serviceName) {
        this.serviceName = serviceName;
        this.log = LoggerFactory.getLogger("com.observe.metrics." + serviceName);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, serviceName.toLowerCase(Locale.ROOT) + "-observability-demo");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void run(String... args) {
        scheduler.scheduleAtFixedRate(this::emitMetrics, 2, 10, TimeUnit.SECONDS);
    }

    private void emitMetrics() {
        try {
            long sample = counter.incrementAndGet();
            MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memoryMxBean.getNonHeapMemoryUsage();
            int simulatedLatencyMs = random.nextInt(35, 650);
            int histogramBucketMs = (simulatedLatencyMs / 100) * 100;

            emit("counter", "demo.requests.total", sample, "count");
            emit("meter", "demo.requests.rate", random.nextDouble(4.0, 42.0), "requests_per_second");
            emit("timer", "demo.call.duration", simulatedLatencyMs, "milliseconds");
            emit("histogram", "demo.call.duration.bucket", histogramBucketMs, "milliseconds");
            emit("gauge", "jvm.memory.heap.used", heap.getUsed(), "bytes");
            emit("gauge", "jvm.memory.non_heap.used", nonHeap.getUsed(), "bytes");
            emit("gauge", "process.cpu.load", processCpuLoad(), "ratio");
        } catch (RuntimeException ex) {
            log.warn("observability demo metric emission failed", ex);
        }
    }

    private void emit(String metricType, String metricName, Number metricValue, String unit) {
        log.info("observability demo metric",
                kv("event_dataset", "demo.metrics"),
                kv("service_name", serviceName),
                kv("metric_type", metricType),
                kv("metric_name", metricName),
                kv("metric_value", metricValue),
                kv("metric_unit", unit),
                kv("metric_timestamp", Instant.now().toString()));
    }

    private double processCpuLoad() {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean extendedOsBean) {
            double load = extendedOsBean.getProcessCpuLoad();
            return load < 0 ? 0 : load;
        }
        return 0;
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }
}
