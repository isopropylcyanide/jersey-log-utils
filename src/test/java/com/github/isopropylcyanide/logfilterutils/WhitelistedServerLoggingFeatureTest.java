package com.github.isopropylcyanide.logfilterutils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.FeatureContext;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WhitelistedServerLoggingFeatureTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testConfigure() {
        FeatureContext featureContext = mock(FeatureContext.class);
        WhitelistedServerLoggingFeature feature = new WhitelistedServerLoggingFeature(new HashSet<>());

        assertTrue(feature.configure(featureContext));
        verify(featureContext, times(1)).register(feature);
    }

    @Test
    public void testFilterLogsRequestWhenURIIsNotExcluded() throws IOException {
        ContainerRequestContext mockRequestContext = mock(ContainerRequestContext.class, Answers.RETURNS_DEEP_STUBS);
        when(mockRequestContext.getUriInfo().getPath()).thenReturn("S3");

        Set<String> excludeUrls = new HashSet<>(Arrays.asList("S1", "S2"));
        WhitelistedServerLoggingFeature feature = new WhitelistedServerLoggingFeature(excludeUrls);
        thrown.expect(NullPointerException.class);
        feature.filter(mockRequestContext);
    }

    @Test
    public void testFilterLogsResponseWhenURIIsNotExcluded() throws IOException {
        ContainerRequestContext mockRequestContext = mock(ContainerRequestContext.class, Answers.RETURNS_DEEP_STUBS);
        when(mockRequestContext.getUriInfo().getPath()).thenReturn("S3");

        Set<String> excludeUrls = new HashSet<>(Arrays.asList("S1", "S2"));
        WhitelistedServerLoggingFeature feature = new WhitelistedServerLoggingFeature(excludeUrls, 100);
        thrown.expect(NullPointerException.class);
        feature.filter(mockRequestContext, mock(ContainerResponseContext.class));
    }
}
