/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.http.server.testing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.QueryStringFilter;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.tracetoken.TraceTokenManager;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.testing.Assertions.assertGreaterThan;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

public class TestTestingHttpServer
{
    @Test
    public void testInitialization()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();
        Map<String, String> params = ImmutableMap.of("sampleInitParameter", "the value");
        TestingHttpServer server = createTestingHttpServer(servlet, params);

        try {
            server.start();
            assertEquals(servlet.getSampleInitParam(), "the value");
            assertGreaterThan(server.getPort(), 0);
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testRequest()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();
        TestingHttpServer server = createTestingHttpServer(servlet, ImmutableMap.<String, String>of());

        try {
            server.start();

            try (HttpClient client = new ApacheHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
                StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

                assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
                assertEquals(servlet.getCallCount(), 1);
            }
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testFilteredRequest()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();
        DummyFilter filter = new DummyFilter();
        TestingHttpServer server = createTestingHttpServerWithFilter(servlet, ImmutableMap.<String, String>of(), filter);

        try {
            server.start();

            try (HttpClient client = new ApacheHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
                StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

                assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
                assertEquals(servlet.getCallCount(), 1);
                assertEquals(filter.getCallCount(), 1);
            }
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testGuiceInjectionWithoutFilters()
            throws Exception
    {
        final DummyServlet servlet = new DummyServlet();

        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        new TestingHttpServerModule(),
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                binder.bind(Servlet.class).annotatedWith(TheServlet.class).toInstance(servlet);
                                binder.bind(new TypeLiteral<Map<String, String>>()
                                {
                                }).annotatedWith(TheServlet.class).toInstance(ImmutableMap.<String, String>of());
                            }
                        });

        Injector injector = app.initialize();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);

        try (HttpClient client = new ApacheHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
            StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(servlet.getCallCount(), 1);
        }
        finally {
            lifeCycleManager.stop();
        }
    }

    @Test
    public void testGuiceInjectionWithFilters()
            throws Exception
    {
        final DummyServlet servlet = new DummyServlet();
        final DummyFilter filter = new DummyFilter();

        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        new TestingHttpServerModule(),
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                binder.bind(Servlet.class).annotatedWith(TheServlet.class).toInstance(servlet);
                                binder.bind(new TypeLiteral<Map<String, String>>()
                                {
                                }).annotatedWith(TheServlet.class).toInstance(ImmutableMap.<String, String>of());
                                newSetBinder(binder, Filter.class, TheServlet.class).addBinding().toInstance(filter);
                            }
                        });

        Injector injector = app.initialize();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);

        try (HttpClient client = new ApacheHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
            StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(servlet.getCallCount(), 1);
            assertEquals(filter.getCallCount(), 1);
        }

        finally {
            lifeCycleManager.stop();
        }
    }

    private TestingHttpServer createTestingHttpServer(DummyServlet servlet, Map<String, String> params)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config, servlet, params);
    }

    private TestingHttpServer createTestingHttpServerWithFilter(DummyServlet servlet, Map<String, String> params, DummyFilter filter)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config, servlet, params, ImmutableSet.<Filter>of(filter), new QueryStringFilter(), new TraceTokenManager());
    }

    static class DummyServlet
            extends HttpServlet
    {
        private String sampleInitParam;
        private int callCount;

        @Override
        public synchronized void init(ServletConfig config)
                throws ServletException
        {
            sampleInitParam = config.getInitParameter("sampleInitParameter");
        }

        public synchronized String getSampleInitParam()
        {
            return sampleInitParam;
        }

        public synchronized int getCallCount()
        {
            return callCount;
        }

        @Override
        protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException
        {
            ++callCount;
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    static class DummyFilter
            implements Filter
    {
        private final AtomicInteger callCount = new AtomicInteger();

        public int getCallCount()
        {
            return callCount.get();
        }

        @Override
        public void init(FilterConfig filterConfig)
                throws ServletException
        {
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
                throws IOException, ServletException
        {
            callCount.incrementAndGet();
            filterChain.doFilter(servletRequest, servletResponse);
        }

        @Override
        public void destroy()
        {
        }
    }
}
