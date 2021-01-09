/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.isopropylcyanide.jerseylogutils.builder;

import org.glassfish.jersey.message.MessageUtils;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedMap;
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

public class RequestResponseBuilder {

    public static final int DEFAULT_MAX_ENTITY_SIZE = 8 * 1024;
    private static final Comparator<Map.Entry<String, List<String>>> COMPARATOR =
            (o1, o2) -> o1.getKey().compareToIgnoreCase(o2.getKey());


    private static final String NOTIFICATION_PREFIX = "* ";
    private static final String REQUEST_PREFIX = "> ";
    private static final String RESPONSE_PREFIX = "< ";

    private final int maxEntitySize;
    private final String entityLoggerProperty;
    private final String loggerProperty;
    private final AtomicLong serverRequestCounter = new AtomicLong(0);
    private final AtomicLong clientRequestCounter = new AtomicLong(0);

    public RequestResponseBuilder(int maxEntitySize, String loggerClass) {
        this.maxEntitySize = maxEntitySize;
        this.loggerProperty = loggerClass + ".id";
        this.entityLoggerProperty = loggerClass + ".entityLogger";
    }

    public StringBuilder buildRequestLog(ContainerRequestContext context) throws IOException {
        long id = serverRequestCounter.incrementAndGet();
        context.setProperty(loggerProperty, id);

        StringBuilder requestBuilder = new StringBuilder();
        buildServerRequestLogContent(requestBuilder, id, context.getMethod(), context.getUriInfo().getRequestUri());
        buildHeaders(requestBuilder, id, REQUEST_PREFIX, context.getHeaders());

        if (context.hasEntity()) {
            context.setEntityStream(
                    logInboundEntity(requestBuilder, context.getEntityStream(), MessageUtils.getCharset(context.getMediaType())));
        }
        return requestBuilder;
    }

    public StringBuilder buildRequestLog(ClientRequestContext context) {
        long id = clientRequestCounter.incrementAndGet();
        context.setProperty(loggerProperty, id);

        StringBuilder requestBuilder = new StringBuilder();
        buildClientRequestLogContent(requestBuilder, id, context.getMethod(), context.getUri());
        buildHeaders(requestBuilder, id, REQUEST_PREFIX, context.getStringHeaders());

        if (context.hasEntity()) {
            OutputStream stream = new LoggingStream(requestBuilder, context.getEntityStream());
            context.setEntityStream(stream);
            context.setProperty(entityLoggerProperty, stream);
        }
        return requestBuilder;
    }

    public StringBuilder buildResponseLog(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object requestId = requestContext.getProperty(loggerProperty);
        long id = requestId != null ? (Long) requestId : this.serverRequestCounter.incrementAndGet();

        StringBuilder responseBuilder = new StringBuilder();
        buildServerResponseLogContent(responseBuilder, id, responseContext.getStatus());
        buildHeaders(responseBuilder, id, RESPONSE_PREFIX, responseContext.getStringHeaders());

        if (responseContext.hasEntity()) {
            OutputStream stream = new LoggingStream(responseBuilder, responseContext.getEntityStream());
            responseContext.setEntityStream(stream);
            requestContext.setProperty(entityLoggerProperty, stream);
        }
        return responseBuilder;
    }

    public StringBuilder buildResponseLog(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Object requestId = requestContext.getProperty(loggerProperty);
        long id = requestId != null ? (Long) requestId : this.clientRequestCounter.incrementAndGet();

        StringBuilder responseBuilder = new StringBuilder();
        buildClientResponseLogContent(responseBuilder, id, responseContext.getStatus());
        buildHeaders(responseBuilder, id, RESPONSE_PREFIX, responseContext.getHeaders());

        if (responseContext.hasEntity()) {
            responseContext.setEntityStream(logInboundEntity(responseBuilder, responseContext.getEntityStream(),
                    MessageUtils.getCharset(responseContext.getMediaType())));
        }
        return responseBuilder;
    }

    public StringBuilder getEntityWriterBuilder(WriterInterceptorContext context) throws IOException {
        LoggingStream stream = (LoggingStream) context.getProperty(entityLoggerProperty);
        context.proceed();
        if (stream != null) {
            return stream.getStringBuilder(MessageUtils.getCharset(context.getMediaType()));
        }
        return null;
    }

    private void buildServerRequestLogContent(StringBuilder b, long id, String method, URI uri) {
        prefixId(b, id).append(NOTIFICATION_PREFIX)
                .append("Server has received a request")
                .append(" on thread ").append(Thread.currentThread().getName())
                .append("\n");
        prefixId(b, id).append(REQUEST_PREFIX).append(method).append(" ")
                .append(uri.toASCIIString()).append("\n");
    }

    private void buildClientRequestLogContent(StringBuilder b, long id, String method, URI uri) {
        prefixId(b, id).append(NOTIFICATION_PREFIX)
                .append("Sending client request")
                .append(" on thread ").append(Thread.currentThread().getName())
                .append("\n");
        prefixId(b, id).append(REQUEST_PREFIX).append(method).append(" ")
                .append(uri.toASCIIString()).append("\n");
    }

    private void buildServerResponseLogContent(StringBuilder b, long id, int status) {
        prefixId(b, id).append(NOTIFICATION_PREFIX)
                .append("Server responded with a response")
                .append(" on thread ").append(Thread.currentThread().getName()).append("\n");
        prefixId(b, id).append(RESPONSE_PREFIX)
                .append(status)
                .append("\n");
    }

    private void buildClientResponseLogContent(StringBuilder b, long id, int status) {
        prefixId(b, id).append(NOTIFICATION_PREFIX)
                .append("Client response received")
                .append(" on thread ").append(Thread.currentThread().getName()).append("\n");
        prefixId(b, id).append(RESPONSE_PREFIX)
                .append(status)
                .append("\n");
    }

    private StringBuilder prefixId(StringBuilder b, long id) {
        b.append(id).append(" ");
        return b;
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
