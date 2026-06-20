package com.observe.common.tracing;

import java.util.Collections;

import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class TracingAutoConfiguration {

    private static final String INSTRUMENTATION_NAME = "devopsstudy";

    @Bean
    @ConditionalOnMissingBean
    SdkTracerProvider sdkTracerProvider() {
        return SdkTracerProvider.builder()
                .setSampler(Sampler.parentBased(Sampler.alwaysOn()))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenTelemetry openTelemetry(SdkTracerProvider sdkTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    OtelCurrentTraceContext otelCurrentTraceContext() {
        return new OtelCurrentTraceContext();
    }

    @Bean
    @ConditionalOnMissingBean
    OtelBaggageManager otelBaggageManager(OtelCurrentTraceContext currentTraceContext) {
        return new OtelBaggageManager(currentTraceContext, Collections.emptyList(), Collections.emptyList());
    }

    @Bean
    @ConditionalOnMissingBean
    Tracer micrometerTracer(OpenTelemetry openTelemetry,
            OtelCurrentTraceContext currentTraceContext,
            OtelBaggageManager baggageManager) {
        return new OtelTracer(
                openTelemetry.getTracer(INSTRUMENTATION_NAME),
                currentTraceContext,
                event -> new Slf4JEventListener().onEvent(event),
                baggageManager);
    }

    @Bean
    @ConditionalOnMissingBean
    Propagator micrometerPropagator(OpenTelemetry openTelemetry) {
        return new OtelPropagator(openTelemetry.getPropagators(), openTelemetry.getTracer(INSTRUMENTATION_NAME));
    }

    @Bean
    ObservationHandler<?> receiverTracingObservationHandler(Tracer tracer, Propagator propagator) {
        return new PropagatingReceiverTracingObservationHandler<>(tracer, propagator);
    }

    @Bean
    ObservationHandler<?> senderTracingObservationHandler(Tracer tracer, Propagator propagator) {
        return new PropagatingSenderTracingObservationHandler<>(tracer, propagator);
    }

    @Bean
    ObservationHandler<?> defaultTracingObservationHandler(Tracer tracer) {
        return new DefaultTracingObservationHandler(tracer);
    }
}
