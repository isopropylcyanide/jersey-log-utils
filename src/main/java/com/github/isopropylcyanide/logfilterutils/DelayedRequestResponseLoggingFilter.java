package com.github.isopropylcyanide.logfilterutils;

import org.glassfish.jersey.message.MessageUtils;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
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

    private static final String NOTIFICATION_PREFIX = "* ";
    private static final String REQUEST_PREFIX = "> ";
    private static final String RESPONSE_PREFIX = "< ";
    private static final String ENTITY_LOGGER_PROPERTY = DelayedRequestResponseLoggingFilter.class.getName() + ".entityLogger";
    private static final String LOGGING_ID_PROPERTY = DelayedRequestResponseLoggingFilter.class.getName() + ".id";

    private static final Comparator<Map.Entry<String, List<String>>> COMPARATOR =
            (o1, o2) -> o1.getKey().compareToIgnoreCase(o2.getKey());

    private final ResponseCondition responseCondition;
    private final int maxEntitySize;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final ThreadLocal<StringBuilder> requestLogCache = new ThreadLocal<>();

    private Logger logger;

    public DelayedRequestResponseLoggingFilter(ResponseCondition responseCondition, int maxEntitySize) {
        this.responseCondition = responseCondition;
        this.maxEntitySize = Math.max(0, maxEntitySize);
        this.logger = Logger.getLogger(DelayedRequestResponseLoggingFilter.class.getName());
    }

    public DelayedRequestResponseLoggingFilter(Logger logger, ResponseCondition responseCondition, int maxEntitySize) {
        this.responseCondition = responseCondition;
        this.maxEntitySize = Math.max(0, maxEntitySize);
        this.logger = logger;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        StringBuilder requestBuilder = getRequestBuilder(requestContext);
        requestLogCache.set(requestBuilder);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        try {
            int status = responseContext.getStatus();
            if (responseCondition.test(status)) {
                StringBuilder requestBuilder = requestLogCache.get();
                if (requestBuilder != null) {
                    log(requestBuilder);
                    StringBuilder responseBuilder = getResponseBuilder(requestContext, responseContext);
                    log(responseBuilder);
                }
            }
        } finally {
            requestLogCache.remove();
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        LoggingStream stream = (LoggingStream) context.getProperty(ENTITY_LOGGER_PROPERTY);
        context.proceed();
        if (stream != null) {
            log(stream.getStringBuilder(MessageUtils.getCharset(context.getMediaType())));
        }
    }

    boolean isRequestLogInCache() {
        return requestLogCache.get() != null;
    }

    private StringBuilder getRequestBuilder(ContainerRequestContext context) throws IOException {
        long id = requestCounter.incrementAndGet();
        context.setProperty(LOGGING_ID_PROPERTY, id);

        StringBuilder requestBuilder = new StringBuilder();
        buildRequestLogContent(requestBuilder, id, context.getMethod(), context.getUriInfo().getRequestUri());
        buildHeaders(requestBuilder, id, REQUEST_PREFIX, context.getHeaders());

        if (context.hasEntity()) {
            context.setEntityStream(
                    logInboundEntity(requestBuilder, context.getEntityStream(), MessageUtils.getCharset(context.getMediaType())));
        }
        return requestBuilder;
    }

    private StringBuilder getResponseBuilder(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object requestId = requestContext.getProperty(LOGGING_ID_PROPERTY);
        long id = requestId != null ? (Long) requestId : this.requestCounter.incrementAndGet();

        StringBuilder responseBuilder = new StringBuilder();
        buildResponseLogContent(responseBuilder, id, responseContext.getStatus());
        buildHeaders(responseBuilder, id, RESPONSE_PREFIX, responseContext.getStringHeaders());

        if (responseContext.hasEntity()) {
            OutputStream stream = new LoggingStream(responseBuilder, responseContext.getEntityStream());
            responseContext.setEntityStream(stream);
            requestContext.setProperty(ENTITY_LOGGER_PROPERTY, stream);
        }
        return responseBuilder;
    }

    private InputStream logInboundEntity(StringBuilder builder, InputStream stream, Charset charset) throws IOException {
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }
        stream.mark(maxEntitySize + 1);
        byte[] entity = new byte[maxEntitySize + 1];
        int entitySize = stream.read(entity);
        builder.append(new String(entity, 0, Math.min(entitySize, maxEntitySize), charset));
        if (entitySize > maxEntitySize) {
            builder.append("...more...");
        }
        builder.append('\n');
        stream.reset();
        return stream;
    }

    private StringBuilder prefixId(StringBuilder b, long id) {
        b.append(id).append(" ");
        return b;
    }

    private void buildRequestLogContent(StringBuilder b, long id, String method, URI uri) {
        prefixId(b, id).append(NOTIFICATION_PREFIX)
                .append("Server has received a request")
                .append(" on thread ").append(Thread.currentThread().getName())
                .append("\n");
        prefixId(b, id).append(REQUEST_PREFIX).append(method).append(" ")
                .append(uri.toASCIIString()).append("\n");
    }

    private void buildResponseLogContent(StringBuilder b, long id, int status) {
        prefixId(b, id).append(NOTIFICATION_PREFIX)
                .append("Server responded with a response")
                .append(" on thread ").append(Thread.currentThread().getName()).append("\n");
        prefixId(b, id).append(RESPONSE_PREFIX)
                .append(status)
                .append("\n");
    }

    private void buildHeaders(StringBuilder b, long id, String prefix, MultivaluedMap<String, String> headers) {
        for (Map.Entry<String, List<String>> headerEntry : getSortedHeaders(headers.entrySet())) {
            List<?> val = headerEntry.getValue();
            String header = headerEntry.getKey();

            if (val.size() == 1) {
                prefixId(b, id).append(prefix).append(header).append(": ").append(val.get(0)).append("\n");
            } else {
                StringBuilder sb = new StringBuilder();
                boolean add = false;
                for (Object s : val) {
                    if (add) {
                        sb.append(',');
                    }
                    add = true;
                    sb.append(s);
                }
                prefixId(b, id).append(prefix).append(header).append(": ").append(sb.toString()).append("\n");
            }
        }
    }

    private SortedSet<Map.Entry<String, List<String>>> getSortedHeaders(Set<Map.Entry<String, List<String>>> headers) {
        TreeSet<Map.Entry<String, List<String>>> sortedHeaders = new TreeSet<>(COMPARATOR);
        sortedHeaders.addAll(headers);
        return sortedHeaders;
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

    private class LoggingStream extends FilterOutputStream {

        private StringBuilder b;
        private ByteArrayOutputStream baos = new ByteArrayOutputStream();

        LoggingStream(StringBuilder b, OutputStream inner) {
            super(inner);
            this.b = b;
        }

        StringBuilder getStringBuilder(Charset charset) {
            byte[] entity = baos.toByteArray();

            b.append(new String(entity, 0, Math.min(entity.length, maxEntitySize), charset));
            if (entity.length > maxEntitySize) {
                b.append("...more...");
            }
            b.append('\n');
            return b;
        }

        @Override
        public void write(int i) throws IOException {
            if (baos.size() <= maxEntitySize) {
                baos.write(i);
            }
            out.write(i);
        }
    }
}
