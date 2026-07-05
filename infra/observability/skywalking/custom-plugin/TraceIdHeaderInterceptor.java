package com.agentplatform.observability.skywalking;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.slf4j.MDC;

// NOTE: Spring Boot 3 uses jakarta.servlet, not javax.servlet (Plan 09 had javax — corrected).
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * SkyWalking custom plugin: binds X-Trace-Id HTTP header to SkyWalking TraceID
 * and propagates via MDC for structured logging (doc 09 §11.1 + §11.3).
 *
 * <p>Mount path: agent-platform-custom-plugin (per agent.config plugin.mount).
 * Build as jar and place at /skywalking/plugins/agent-platform-custom-plugin.jar.</p>
 *
 * <p>This interceptor enhances Spring DispatcherServlet's doDispatch / FrameworkHandler
 * methods to extract the X-Trace-Id header and inject it into MDC for Logback
 * structured JSON output.</p>
 */
public class TraceIdHeaderInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, MethodInterceptResult result) {
        if (allArguments == null || allArguments.length == 0
                || !(allArguments[0] instanceof HttpServletRequest)) {
            return;
        }
        HttpServletRequest req = (HttpServletRequest) allArguments[0];
        String traceId = req.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            // Use SkyWalking's global trace id if no incoming header
            traceId = ContextManager.getGlobalTraceId();
        }
        MDC.put(MDC_TRACE_ID, traceId);
        // Bind to current span for cross-system correlation
        AbstractSpan span = ContextManager.activeSpan();
        if (span != null) {
            span.tag("x-trace-id", traceId);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                               Class<?>[] argumentsTypes, Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        // MDC cleared by application-layer TraceIdFilter in finally block
    }
}
