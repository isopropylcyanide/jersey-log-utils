package com.github.isopropylcyanide.jerseylogutils;

import com.github.isopropylcyanide.jerseylogutils.model.URIContext;
import jersey.repackaged.com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class WhitelistedClientLoggingFilterTest {

    @Mock
    private Logger logger;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ClientRequestContext request;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ClientResponseContext response;

    private WhitelistedClientLoggingFilter loggingFilter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFilterDoesNotLogRequestWhenURIMethodPairIsExcluded() throws URISyntaxException {
        when(request.getUri()).thenReturn(new URI("S3"));
        when(request.getMethod()).thenReturn(HttpMethod.POST);

        Set<URIContext> contexts = Sets.newHashSet(
                new URIContext(HttpMethod.GET, "S2"),
                new URIContext(HttpMethod.POST, "S3")
        );
        loggingFilter = new WhitelistedClientLoggingFilter(contexts, logger);
        loggingFilter.filter(request);
        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterDoesNotLogRequestWhenURIMethodPairIsExcludedForAnyVerb() throws URISyntaxException {
        when(request.getUri()).thenReturn(new URI("S3"));
        when(request.getMethod()).thenReturn(HttpMethod.DELETE);

        Set<URIContext> contexts = Sets.newHashSet(
                new URIContext(HttpMethod.GET, "S2"),
                new URIContext("S3")
        );
        loggingFilter = new WhitelistedClientLoggingFilter(contexts, logger);
        loggingFilter.filter(request);
        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterDoesNotLogRequestWhenEntityIsNotEmpty() throws URISyntaxException {
        when(request.getUri()).thenReturn(new URI("S3"));
        when(request.hasEntity()).thenReturn(true);
        when(request.getMethod()).thenReturn(HttpMethod.GET);

        Set<URIContext> contexts = Sets.newHashSet(
                new URIContext(HttpMethod.GET, "S1"),
                new URIContext(HttpMethod.POST, "S2")
        );
        loggingFilter = new WhitelistedClientLoggingFilter(contexts, logger, 1000);
        loggingFilter.filter(request);
        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterLogsRequestWhenEntityIsEmpty() throws URISyntaxException {
        Set<URIContext> contexts = Sets.newHashSet(
                new URIContext(HttpMethod.GET, "S1"),
                new URIContext(HttpMethod.PUT, "S2")
        );
        when(request.getUri()).thenReturn(new URI("S2"));
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.hasEntity()).thenReturn(false);

        loggingFilter = new WhitelistedClientLoggingFilter(contexts, logger, 1000);
        loggingFilter.filter(request);
        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(argCaptor.capture());

        String requestStream = argCaptor.getAllValues().get(0);
        assertTrue(requestStream.contains("Sending client request"));
        assertTrue(requestStream.contains("S2"));
    }

    @Test
    public void testFilterDoesNotLogResponseWhenURIIsExcluded() throws URISyntaxException, IOException {
        when(request.getUri()).thenReturn(new URI("S6"));
        when(request.getMethod()).thenReturn(HttpMethod.PUT);

        Set<URIContext> contexts = Collections.singleton(
                new URIContext(HttpMethod.PUT, "S6")
        );
        loggingFilter = new WhitelistedClientLoggingFilter(contexts, logger);
        loggingFilter.filter(request, response);
        verifyZeroInteractions(logger);
    }

    @Test
    public void testFilterLogsResponseWhenEntityIsEmpty() throws URISyntaxException, IOException {
        when(request.getUri()).thenReturn(new URI("S1"));
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(response.hasEntity()).thenReturn(false);

        Set<URIContext> contexts = Sets.newHashSet(
                new URIContext(HttpMethod.GET, "S1"),
                new URIContext(HttpMethod.PUT, "S2")
        );
        loggingFilter = new WhitelistedClientLoggingFilter(contexts, logger);
        loggingFilter.filter(request, response);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(argCaptor.capture());

        String requestStream = argCaptor.getAllValues().get(0);
        assertTrue(requestStream.contains("Client response received"));
    }
}
