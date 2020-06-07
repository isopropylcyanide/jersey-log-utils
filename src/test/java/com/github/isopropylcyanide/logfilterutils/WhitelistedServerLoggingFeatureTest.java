package com.github.isopropylcyanide.logfilterutils;

import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class WhitelistedServerLoggingFeatureTest {

    @Mock
    private Logger logger;

    private WhitelistedServerLoggingFilter loggingFilter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFilterDoesNotLogRequestWhenURIIstExcluded() throws IOException, URISyntaxException {
        ContainerRequest request = stubContainerRequest("S4");
        Set<String> excludeUrls = new HashSet<>(Arrays.asList("S4", "S2"));
        loggingFilter = new WhitelistedServerLoggingFilter(excludeUrls, logger);
        loggingFilter.filter(request);

        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterLogsRequestWhenURIIsNotExcluded() throws IOException, URISyntaxException {
        ContainerRequest request = stubContainerRequest("S3");
        Set<String> excludeUrls = new HashSet<>(Arrays.asList("S1", "S2"));
        loggingFilter = new WhitelistedServerLoggingFilter(excludeUrls, logger, 1000);
        loggingFilter.filter(request);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(argCaptor.capture());

        String requestStream = argCaptor.getAllValues().get(0);
        assertTrue(requestStream.contains("Server has received a request on thread main"));
        assertTrue(requestStream.contains("S3"));
    }

    @Test
    public void testFilterDoesNotLogResponseWhenURIIsExcluded() throws URISyntaxException {
        Response response4xx = Response.status(400).entity("OK").build();
        ContainerRequest request = stubContainerRequest("S6");
        ContainerResponse response = new ContainerResponse(request, response4xx);

        Set<String> excludeUrls = new HashSet<>(Collections.singletonList("S6"));
        loggingFilter = new WhitelistedServerLoggingFilter(excludeUrls, logger);
        loggingFilter.filter(request, response);
        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterLogsResponseWhenURIIsNotExcluded() throws URISyntaxException {
        Response response4xx = Response.status(400).entity("OK").build();
        ContainerRequest request = stubContainerRequest("S3");
        ContainerResponse response = new ContainerResponse(request, response4xx);

        Set<String> excludeUrls = new HashSet<>(Arrays.asList("S1", "S2"));
        loggingFilter = new WhitelistedServerLoggingFilter(excludeUrls, logger);
        loggingFilter.filter(request, response);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(argCaptor.capture());

        String responseStream = argCaptor.getAllValues().get(0);
        assertTrue(responseStream.contains("Server responded with a response on thread"));
    }

    private ContainerRequest stubContainerRequest(String id) throws URISyntaxException {
        URI baseURI = new URI("http://127.0.0.1:8100/");
        URI requestURI = new URI("http://127.0.0.1:8100/" + id);
        return new ContainerRequest(baseURI, requestURI, HttpMethod.GET, new MockSecurityContext(), new MockPropertiesDelegate());
    }

    class MockSecurityContext implements SecurityContext {

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return null;
        }
    }

    class MockPropertiesDelegate implements PropertiesDelegate {

        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public Collection<String> getPropertyNames() {
            return null;
        }

        @Override
        public void setProperty(String name, Object object) {

        }

        @Override
        public void removeProperty(String name) {

        }
    }
}
