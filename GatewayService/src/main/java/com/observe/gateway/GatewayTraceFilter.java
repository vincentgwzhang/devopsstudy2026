package com.observe.gateway;

import com.observe.common.tracing.MdcSupport;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
class GatewayTraceFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayTraceFilter.class);

    private final ObjectProvider<Tracer> tracerProvider;

    GatewayTraceFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            var traceScope = MdcSupport.openSpan(tracerProvider.getIfAvailable(), "GatewayService forwarding request");
            log.info("GatewayService forwarding request with traceId={} spanId={}",
                    MdcSupport.traceId().orElse("-"),
                    MdcSupport.spanId().orElse("-"));
            return chain.filter(exchange)
                    .doFinally(signalType -> traceScope.close());
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
