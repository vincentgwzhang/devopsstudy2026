package com.observe.payment;

import java.io.IOException;

import com.observe.common.tracing.MdcSupport;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class HttpTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpTraceFilter.class);

    private final ObjectProvider<Tracer> tracerProvider;

    HttpTraceFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try (var ignored = MdcSupport.openSpan(tracerProvider.getIfAvailable(), "PaymentService received request")) {
            log.info("PaymentService received a calling");
            filterChain.doFilter(request, response);
        }
    }
}
