package com.github.isopropylcyanide.jerseylogutils;

import com.github.isopropylcyanide.jerseylogutils.DelayedServerRequestResponseLoggingFilter.ResponseCondition;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Collection;
import java.util.logging.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class DelayedServerRequestResponseLoggingFilterTest {

    private DelayedServerRequestResponseLoggingFilter loggingFilter;

    @Mock
    private Logger logger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFilterContainerRequestContextAddsValueInThreadLocalCache() throws URISyntaxException, IOException {
        loggingFilter = new DelayedServerRequestResponseLoggingFilter(ResponseCondition.ON_RESPONSE_2XX, 100);
        ContainerRequest request = stubContainerRequest();
        loggingFilter.filter(request);

        assertTrue(loggingFilter.isRequestLogInCache());
        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterContainerResponseLogsRequestAndResponseWhenResponseConditionIsMet() throws URISyntaxException, IOException {
        loggingFilter = new DelayedServerRequestResponseLoggingFilter(logger, ResponseCondition.ON_RESPONSE_4XX_5XX, 100);
        ContainerRequest request = stubContainerRequest();
        request.setEntityStream(new ByteArrayInputStream("{Request entity}".getBytes()));

        Response response4xx = Response.status(400).build();
        ContainerResponse response = new ContainerResponse(stubContainerRequest(), response4xx);

        loggingFilter.filter(request);
        loggingFilter.filter(request, response);
        assertFalse(loggingFilter.isRequestLogInCache());

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(2)).info(argCaptor.capture());

        String requestStream = argCaptor.getAllValues().get(0);
        String responseStream = argCaptor.getAllValues().get(1);

        assertTrue(requestStream.contains("Server has received a request on thread main"));
        assertTrue(requestStream.contains("{Request entity}"));
        assertTrue(responseStream.contains("Server responded with a response on thread main"));
        assertTrue(responseStream.contains("< 400"));
    }

    @Test
    public void testFilterContainerResponseLogsRequestButNotResponseWhenConditionIsMetAndResponseHasEntity() throws URISyntaxException, IOException {
        loggingFilter = new DelayedServerRequestResponseLoggingFilter(logger, ResponseCondition.ON_RESPONSE_4XX_5XX, 100);
        ContainerRequest request = stubContainerRequest();
        request.setEntityStream(new ByteArrayInputStream("{Request entity}".getBytes()));

        Response response4xx = Response.status(400).entity("Bad request").build();
        ContainerResponse response = new ContainerResponse(stubContainerRequest(), response4xx);

        loggingFilter.filter(request);
        loggingFilter.filter(request, response);
        assertFalse(loggingFilter.isRequestLogInCache());

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(argCaptor.capture());

        String requestStream = argCaptor.getAllValues().get(0);
        assertTrue(requestStream.contains("Server has received a request on thread main"));
        assertTrue(requestStream.contains("{Request entity}"));
    }

    @Test
    public void testFilterContainerResponseDoesntLogRequestAndResponseWhenResponseConditionIsNotMetButCacheIsCleared() throws URISyntaxException, IOException {
        loggingFilter = new DelayedServerRequestResponseLoggingFilter(logger, ResponseCondition.ON_RESPONSE_5XX, 100);
        ContainerRequest request = stubContainerRequest();

        Response response200 = Response.status(200).build();
        ContainerResponse response = new ContainerResponse(request, response200);

        loggingFilter.filter(request);
        loggingFilter.filter(request, response);
        assertFalse(loggingFilter.isRequestLogInCache());
        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterContainerResponseDoesntLogRequestAndResponseWhenResponseConditionIsNotMetForConflictButCacheIsCleared() throws URISyntaxException, IOException {
        loggingFilter = new DelayedServerRequestResponseLoggingFilter(logger, ResponseCondition.ON_RESPONSE_4XX_5XX_NON_CONFLICT, 100);
        ContainerRequest request = stubContainerRequest();

        Response response409 = Response.status(409).build();
        ContainerResponse response = new ContainerResponse(request, response409);

        loggingFilter.filter(request);
        loggingFilter.filter(request, response);
        assertFalse(loggingFilter.isRequestLogInCache());
        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterContainerResponseDoesntLogRequestAndResponseWhenResponseConditionIsNotFoundForConflictButCacheIsCleared() throws URISyntaxException, IOException {
        loggingFilter = new DelayedServerRequestResponseLoggingFilter(logger, ResponseCondition.ON_RESPONSE_4XX_5XX_NON_NOT_FOUND, 100);
        ContainerRequest request = stubContainerRequest();

        Response response409 = Response.status(404).build();
        ContainerResponse response = new ContainerResponse(request, response409);

        loggingFilter.filter(request);
        loggingFilter.filter(request, response);
        assertFalse(loggingFilter.isRequestLogInCache());
        verifyZeroInteractions(logger);
    }

    private ContainerRequest stubContainerRequest() throws URISyntaxException {
        URI baseURI = new URI("http://127.0.0.1:8100/");
        URI requestURI = new URI("http://127.0.0.1:8100/resource/test");
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
