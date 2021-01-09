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
package com.github.isopropylcyanide.jerseylogutils;

import com.github.isopropylcyanide.jerseylogutils.builder.RequestResponseBuilder;
import com.github.isopropylcyanide.jerseylogutils.model.URIContext;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;

import static com.github.isopropylcyanide.jerseylogutils.builder.RequestResponseBuilder.DEFAULT_MAX_ENTITY_SIZE;


/**
 * This logging filter helps exclude logging client requests and responses for URIs that match a set of excluded URIs
 */
public class WhitelistedClientLoggingFilter implements ClientRequestFilter, ClientResponseFilter, WriterInterceptor {

    private final RequestResponseBuilder requestResponseBuilder;
    private final Logger logger;

    /**
     * A set of exclude contexts for which the client request and response logging will not be performed
     * if the URI/Method pair matches any of the path in the set of blacklisted paths.
     */
    private final Set<URIContext> excludeContexts;

    public WhitelistedClientLoggingFilter(Set<URIContext> excludeContexts) {
        this.excludeContexts = excludeContexts;
        this.logger = Logger.getLogger(WhitelistedClientLoggingFilter.class.getName());
        this.requestResponseBuilder = new RequestResponseBuilder(DEFAULT_MAX_ENTITY_SIZE, WhitelistedClientLoggingFilter.class.getName());
    }

    public WhitelistedClientLoggingFilter(Set<URIContext> excludeContexts, int maxEntitySize) {
        this.excludeContexts = excludeContexts;
        this.logger = Logger.getLogger(WhitelistedClientLoggingFilter.class.getName());
        this.requestResponseBuilder = new RequestResponseBuilder(Math.max(0, maxEntitySize), WhitelistedClientLoggingFilter.class.getName());
    }

    public WhitelistedClientLoggingFilter(Set<URIContext> excludeContexts, Logger logger, int maxEntitySize) {
        this.excludeContexts = excludeContexts;
        this.logger = logger;
        this.requestResponseBuilder = new RequestResponseBuilder(maxEntitySize, WhitelistedClientLoggingFilter.class.getName());
    }

    public WhitelistedClientLoggingFilter(Set<URIContext> excludeContexts, Logger logger) {
        this.excludeContexts = excludeContexts;
        this.logger = logger;
        this.requestResponseBuilder = new RequestResponseBuilder(DEFAULT_MAX_ENTITY_SIZE, WhitelistedClientLoggingFilter.class.getName());
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        String path = requestContext.getUri().getPath();
        String httpMethod = requestContext.getMethod();

        if (isWhiteListed(path, httpMethod, excludeContexts)) {
            StringBuilder requestLog = requestResponseBuilder.buildRequestLog(requestContext);
            if (!requestContext.hasEntity()) {
                //if entity is present, it will be called by the client run time through the interceptor
                log(requestLog);
            }
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        String path = requestContext.getUri().getPath();
        String httpMethod = requestContext.getMethod();

        if (isWhiteListed(path, httpMethod, excludeContexts)) {
            StringBuilder responseLog = requestResponseBuilder.buildResponseLog(requestContext, responseContext);
            log(responseLog);
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        log(requestResponseBuilder.getEntityWriterBuilder(context));
    }

    private boolean isWhiteListed(String path, String httpMethod, Set<URIContext> URIContexts) {
        for (URIContext context : URIContexts) {
            if (path.contains(context.getPath())) {
                if (context.getHttpMethod() != null) {
                    return !context.getHttpMethod().equalsIgnoreCase(httpMethod);
                }
                return false;
            }
        }
        return true;
    }

    private void log(StringBuilder b) {
        if (logger != null && b != null) {
            String msg = b.toString();
            logger.info(msg);
        }
    }

}
