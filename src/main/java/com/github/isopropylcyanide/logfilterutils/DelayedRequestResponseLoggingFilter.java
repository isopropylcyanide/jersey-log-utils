package com.github.isopropylcyanide.logfilterutils;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * This class delays the logging of request and response events. A standard logging filter
 * when registered within a Jersey environment starts logging requests and responses as the
 * request moves in its lifecycle, which is near instantaneously.
 * <p>
 * While pretty convenient for normal use cases, for high QPS systems, we end up setting a
 * log level for such a logger to that of an ERROR or WARN to avoid log file abuse. This results
 * in losing the request log unless it is done by the application. We achieve a trade off here by
 * logging requests based on a certain predicate. The most common being when the response is
 * erroneous.
 * <p>
 * This class is meant to log asynchronously and not do any heavy lifting. It directly affects the
 * perceived latency for the consumers. The operations here are optimized for this very effect.
 *
 * @implNote Because the LoggingFilter is a final class and the methods directly log, we couldn't
 * compose our class with an instance of {@link org.glassfish.jersey.filter.LoggingFilter}, as we
 * have to log with a delay. As such, some relevant source has been ingrained for preparing builders.
 * <br>
 * @implNote This class uses a thread local to cache request serialization in the form of a builder
 * If the response received from service passes the supplied predicate, then both request and response
 * will be logged or else nothing will be logged.
 * <p>
 * Note that the {{@link #filter(ContainerRequestContext, ContainerResponseContext)}} block for response
 * is always run, and so the thread local is guaranteed to be garbage collected without leaks.
 */
public class DelayedRequestResponseLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {

    private final RequestResponseBuilder requestResponseBuilder;
    private final ResponseCondition responseCondition;
    private final ThreadLocal<StringBuilder> requestLogCache = new ThreadLocal<>();
    private final Logger logger;

    public DelayedRequestResponseLoggingFilter(ResponseCondition responseCondition, int maxEntitySize) {
        this.requestResponseBuilder = new RequestResponseBuilder(Math.max(0, maxEntitySize), DelayedRequestResponseLoggingFilter.class.getName());
        this.responseCondition = responseCondition;
        this.logger = Logger.getLogger(DelayedRequestResponseLoggingFilter.class.getName());
    }

    public DelayedRequestResponseLoggingFilter(Logger logger, ResponseCondition responseCondition, int maxEntitySize) {
        this.responseCondition = responseCondition;
        this.requestResponseBuilder = new RequestResponseBuilder(Math.max(0, maxEntitySize), DelayedRequestResponseLoggingFilter.class.getName());
        this.logger = logger;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        StringBuilder requestLogBuilder = this.requestResponseBuilder.buildRequestLog(requestContext);
        requestLogCache.set(requestLogBuilder);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        try {
            int status = responseContext.getStatus();
            if (responseCondition.test(status)) {
                StringBuilder requestLogBuilder = requestLogCache.get();
                if (requestResponseBuilder != null) {
                    log(requestLogBuilder);
                    StringBuilder responseBuilder = requestResponseBuilder.buildResponseLog(requestContext, responseContext);
                    log(responseBuilder);
                }
            }
        } finally {
            requestLogCache.remove();
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        log(requestResponseBuilder.getEntityWriterBuilder(context));
    }

    boolean isRequestLogInCache() {
        return requestLogCache.get() != null;
    }

    private void log(StringBuilder b) {
        if (logger != null && b != null) {
            String msg = b.toString();
            logger.info(msg);
        }
    }

    public enum ResponseCondition implements Predicate<Integer> {

        ON_RESPONSE_4XX_5XX {
            @Override
            public boolean test(Integer responseCode) {
                return responseCode >= 400 && responseCode <= 599;
            }
        },

        ON_RESPONSE_4XX_5XX_NON_CONFLICT {
            @Override
            public boolean test(Integer responseCode) {
                return responseCode >= 400 && responseCode <= 599 && responseCode != 409;
            }
        },

        ON_RESPONSE_4XX_5XX_NON_NOT_FOUND {
            @Override
            public boolean test(Integer responseCode) {
                return responseCode >= 400 && responseCode <= 599 && responseCode != 404;
            }
        },

        ON_RESPONSE_5XX {
            @Override
            public boolean test(Integer responseCode) {
                return responseCode >= 500 && responseCode <= 599;
            }
        },

        ON_RESPONSE_2XX {
            @Override
            public boolean test(Integer responseCode) {
                return responseCode >= 400 && responseCode <= 499;
            }
        }
    }
}
