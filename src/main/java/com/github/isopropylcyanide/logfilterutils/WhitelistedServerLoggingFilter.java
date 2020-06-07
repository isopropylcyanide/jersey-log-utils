package com.github.isopropylcyanide.logfilterutils;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This logging filter helps exclude logging requests and responses for URIs that match a set of excluded URIs
 */
public class WhitelistedServerLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {

    private static final int DEFAULT_MAX_ENTITY_SIZE = 8 * 1024;
    private final RequestResponseBuilder requestResponseBuilder;
    private final Logger logger;

    /**
     * A get of paths for which the server request and response logging will not be performed
     * if the URI matches any of the path specified in this get
     */
    private final Set<String> excludedPaths;

    public WhitelistedServerLoggingFilter(Set<String> excludedPaths, Logger logger) {
        this.excludedPaths = excludedPaths;
        this.logger = logger;
        this.requestResponseBuilder = new RequestResponseBuilder(DEFAULT_MAX_ENTITY_SIZE, WhitelistedServerLoggingFilter.class.getName());
    }

    public WhitelistedServerLoggingFilter(Set<String> excludedPaths, Logger logger, int maxEntitySize) {
        this.excludedPaths = excludedPaths;
        this.logger = logger;
        this.requestResponseBuilder = new RequestResponseBuilder(maxEntitySize, WhitelistedServerLoggingFilter.class.getName());
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (excludedPaths.stream().noneMatch(path::contains)) {
            StringBuilder requestLog = requestResponseBuilder.buildRequestLog(requestContext);
            log(requestLog);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String path = requestContext.getUriInfo().getPath();
        if (excludedPaths.stream().noneMatch(path::contains)) {
            StringBuilder responseLog = requestResponseBuilder.buildResponseLog(requestContext, responseContext);
            if (responseContext.hasEntity()) {
                log(responseLog);
            }
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        log(requestResponseBuilder.getEntityWriterBuilder(context));
    }

    private void log(StringBuilder b) {
        if (logger != null && b != null) {
            String msg = b.toString();
            logger.info(msg);
        }
    }
}
