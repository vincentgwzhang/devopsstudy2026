package com.observe.common.tracing;

import java.util.Optional;

import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

public final class MdcSupport {

    private MdcSupport() {
    }

    public static void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    public static void remove(String key) {
        MDC.remove(key);
    }

    public static Optional<String> get(String key) {
        return Optional.ofNullable(MDC.get(key));
    }

    public static void clear() {
        MDC.clear();
    }

    public static void enrichFromTracer(Tracer tracer) {
        if (tracer == null || tracer.currentSpan() == null) {
            return;
        }

        enrichFromContext(tracer.currentSpan().context());
    }

    public static TraceScope openSpan(Tracer tracer, String spanName) {
        if (tracer == null) {
            return TraceScope.noop();
        }

        if (tracer.currentSpan() != null) {
            enrichFromTracer(tracer);
            return TraceScope.current();
        }

        ScopedSpan scopedSpan = tracer.startScopedSpan(spanName);
        enrichFromContext(scopedSpan.context());
        return TraceScope.scoped(scopedSpan);
    }

    private static void enrichFromContext(io.micrometer.tracing.TraceContext context) {
        put(MdcKeys.TRACE_ID, context.traceId());
        put(MdcKeys.SPAN_ID, context.spanId());
        put(MdcKeys.PARENT_SPAN_ID, context.parentId());
    }

    public static Optional<String> traceId() {
        return get(MdcKeys.TRACE_ID);
    }

    public static Optional<String> spanId() {
        return get(MdcKeys.SPAN_ID);
    }

    public static Optional<String> parentSpanId() {
        return get(MdcKeys.PARENT_SPAN_ID);
    }

    public static final class TraceScope implements AutoCloseable {

        private final ScopedSpan scopedSpan;

        private TraceScope(ScopedSpan scopedSpan) {
            this.scopedSpan = scopedSpan;
        }

        private static TraceScope noop() {
            return new TraceScope(null);
        }

        private static TraceScope current() {
            return new TraceScope(null);
        }

        private static TraceScope scoped(ScopedSpan scopedSpan) {
            return new TraceScope(scopedSpan);
        }

        @Override
        public void close() {
            try {
                if (scopedSpan != null) {
                    scopedSpan.end();
                }
            } finally {
                MdcSupport.clear();
            }
        }
    }
}
