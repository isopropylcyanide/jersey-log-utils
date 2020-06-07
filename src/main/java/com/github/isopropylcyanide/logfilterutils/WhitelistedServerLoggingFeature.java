package com.github.isopropylcyanide.logfilterutils;

import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.logging.LoggingFeature;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This logging filter helps exclude logging requests and responses for URIs that match a set of excluded URIs
 */
public class WhitelistedServerLoggingFeature extends LoggingFeature implements ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {

    private static final int DEFAULT_MAX_ENTITY_SIZE = 8 * 1024;

    /**
     * A get of paths for which the server request and response logging will not be performed
     * if the URI matches any of the path specified in this get
     */
    private final Set<String> excludedPaths;

    private final int maxEntitySize;

    /**
     * org.glassfish.jersey.logging.ServerLoggingFilter is not public and there's no way to extend the
     * default logging behaviour. Thus this feature class is composed with the deprecated logging filter
     * class to achieve code reuse. Otherwise the only solution is to deal with the log masks and string
     * or byte stream manipulation manually
     * <p>
     * Note: LoggingFilter is deprecated in Jersey versions > 2.25.1 and will be removed in 2.26
     */
    private final LoggingFilter loggingFilter;

    public WhitelistedServerLoggingFeature(Set<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
        this.maxEntitySize = DEFAULT_MAX_ENTITY_SIZE;
        this.loggingFilter = new LoggingFilter(Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME), DEFAULT_MAX_ENTITY_SIZE);
    }

    public WhitelistedServerLoggingFeature(Set<String> excludedPaths, int maxEntitySize) {
        this.excludedPaths = excludedPaths;
        this.maxEntitySize = Math.max(0, maxEntitySize);
        this.loggingFilter = new LoggingFilter(Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME), maxEntitySize);
    }

    @Override
    public boolean configure(FeatureContext context) {
        context.register(this);
        return true;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (excludedPaths.stream().noneMatch(path::contains)) {
            loggingFilter.filter(requestContext);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (excludedPaths.stream().noneMatch(path::contains)) {
            loggingFilter.filter(requestContext, responseContext);
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        loggingFilter.aroundWriteTo(context);
    }
}
