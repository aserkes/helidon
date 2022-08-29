/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.staticcontent;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.HeadersServerResponse;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpException;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.RequestException;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.webserver.http.RoutedPath;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.common.http.Http.Header.ETAG;
import static io.helidon.common.http.Http.Header.IF_MATCH;
import static io.helidon.common.http.Http.Header.IF_NONE_MATCH;
import static io.helidon.common.http.Http.Header.LOCATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link StaticContentHandler}.
 */
class StaticContentHandlerTest {

    @Test
    void etag_InNoneMatch_NotAccept() {
        HeadersServerRequest req = mock(HeadersServerRequest.class);
        when(req.contains(IF_NONE_MATCH)).thenReturn(true);
        when(req.contains(IF_MATCH)).thenReturn(false);
        when(req.get(IF_NONE_MATCH)).thenReturn(IF_NONE_MATCH.withValue("\"ccc\"", "\"ddd\""));
        HeadersServerResponse res = mock(HeadersServerResponse.class);
        StaticContentHandler.processEtag("aaa", req, res);
        verify(res).set(ETAG.withValue("\"aaa\""));
    }

    @Test
    void etag_InNoneMatch_Accept() {
        HeadersServerRequest req = mock(HeadersServerRequest.class);
        when(req.contains(IF_NONE_MATCH)).thenReturn(true);
        when(req.contains(IF_MATCH)).thenReturn(false);
        when(req.get(IF_NONE_MATCH)).thenReturn(IF_NONE_MATCH.withValue("\"ccc\"", "W/\"aaa\""));
        HeadersServerResponse res = mock(HeadersServerResponse.class);
        assertHttpException(() -> StaticContentHandler.processEtag("aaa", req, res), Http.Status.NOT_MODIFIED_304);
        verify(res).set(ETAG.withValue("\"aaa\""));
    }

    @Test
    void etag_InMatch_NotAccept() {
        HeadersServerRequest req = mock(HeadersServerRequest.class);
        when(req.contains(IF_NONE_MATCH)).thenReturn(false);
        when(req.contains(IF_MATCH)).thenReturn(true);
        when(req.get(IF_MATCH)).thenReturn(IF_MATCH.withValue("\"ccc\"", "\"ddd\""));
        HeadersServerResponse res = mock(HeadersServerResponse.class);
        assertHttpException(() -> StaticContentHandler.processEtag("aaa", req, res), Http.Status.PRECONDITION_FAILED_412);
        verify(res).set(ETAG.withValue("\"aaa\""));
    }

    @Test
    void etag_InMatch_Accept() {
        HeadersServerRequest req = mock(HeadersServerRequest.class);
        when(req.contains(IF_NONE_MATCH)).thenReturn(false);
        when(req.contains(IF_MATCH)).thenReturn(true);
        when(req.get(IF_MATCH)).thenReturn(IF_MATCH.withValue("\"ccc\"", "\"aaa\""));
        HeadersServerResponse res = mock(HeadersServerResponse.class);
        StaticContentHandler.processEtag("aaa", req, res);
        verify(res).set(ETAG.withValue("\"aaa\""));
    }

    @Test
    void ifModifySince_Accept() {
        ZonedDateTime modified = ZonedDateTime.now();
        HeadersServerRequest req = mock(HeadersServerRequest.class);
        Mockito.doReturn(Optional.of(modified.minusSeconds(60))).when(req).ifModifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifUnmodifiedSince();
        HeadersServerResponse res = mock(HeadersServerResponse.class);
        StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res);
    }

    @Test
    void ifModifySince_NotAccept() {
        ZonedDateTime modified = ZonedDateTime.now();
        HeadersServerRequest req = mock(HeadersServerRequest.class);
        Mockito.doReturn(Optional.of(modified)).when(req).ifModifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifUnmodifiedSince();
        HeadersServerResponse res = mock(HeadersServerResponse.class);
        assertHttpException(() -> StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res),
                            Http.Status.NOT_MODIFIED_304);
    }

    @Test
    void ifUnmodifySince_Accept() {
        ZonedDateTime modified = ZonedDateTime.now();
        HeadersServerRequest req = mock(HeadersServerRequest.class);
        Mockito.doReturn(Optional.of(modified)).when(req).ifUnmodifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifModifiedSince();
        HeadersServerResponse res = mock(HeadersServerResponse.class);
        StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res);
    }

    @Test
    void ifUnmodifySince_NotAccept() {
        ZonedDateTime modified = ZonedDateTime.now();
        HeadersServerRequest req = mock(HeadersServerRequest.class);
        Mockito.doReturn(Optional.of(modified.minusSeconds(60))).when(req).ifUnmodifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifModifiedSince();
        HeadersServerResponse res = mock(HeadersServerResponse.class);
        assertHttpException(() -> StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res),
                            Http.Status.PRECONDITION_FAILED_412);
    }

    @Test
    void redirect() {
        HeadersServerResponse resh = mock(HeadersServerResponse.class);
        ServerResponse res = mock(ServerResponse.class);
        ServerRequest req = mock(ServerRequest.class);
        when(res.headers()).thenReturn(resh);
        StaticContentHandler.redirect(req, res, "/foo/");
        verify(res).status(Http.Status.MOVED_PERMANENTLY_301);
        verify(res).header(LOCATION.withValue("/foo/"));
        verify(res).send();
    }

    @Test
    void handleRoot() {
        ServerRequest request = mockRequestWithPath(Http.Method.GET, "/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(request, response);
        verify(response, never()).next();
        assertThat(handler.path, is(Paths.get(".").toAbsolutePath().normalize()));
    }

    @Test
    void handleValid() {
        ServerRequest request = mockRequestWithPath(Http.Method.GET, "/foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(request, response);
        verify(response, never()).next();
        assertThat(handler.path, is(Paths.get("foo/some.txt").toAbsolutePath().normalize()));
    }

    @Test
    void handleOutside() {
        ServerRequest request = mockRequestWithPath(Http.Method.GET, "/../foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(request, response);
        verify(response).next();
        assertThat(handler.counter.get(), is(0));
    }

    @Test
    void handleNextOnFalse() {
        ServerRequest request = mockRequestWithPath(Http.Method.GET, "/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(false);
        handler.handle(request, response);
        verify(response).next();
        assertThat(handler.counter.get(), is(1));
    }

    @Test
    void classpathHandleSpaces() {
        ServerRequest request = mockRequestWithPath(Http.Method.GET, "foo/I have spaces.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestClassPathContentHandler handler = TestClassPathContentHandler.create();
        handler.handle(request, response);
        verify(response, never()).next();
        assertThat(handler.counter.get(), is(1));
    }

    private static void assertHttpException(Runnable runnable, Http.Status status) {
        try {
            runnable.run();
            throw new AssertionError("Expected HttpException was not thrown!");
        } catch (HttpException he) {
            if (status != null && status.code() != he.status().code()) {
                throw new AssertionError("Unexpected status in RequestException. "
                                                 + "(Expected: " + status.code() + ", Actual: " + status.code() + ")");
            }
        }
    }

    private ServerRequest mockRequestWithPath(Http.Method method, String path) {
        UriPath uriPath = UriPath.create(path);
        HttpPrologue prologue = HttpPrologue.create("HTTP",
                                                    "1.1",
                                                    method,
                                                    uriPath,
                                                    UriQuery.empty(),
                                                    UriFragment.empty());
        ServerRequest request = mock(ServerRequest.class);
        when(request.prologue()).thenReturn(prologue);
        when(request.path()).thenReturn(new RoutedPath() {
            @Override
            public Parameters pathParameters() {
                return Parameters.empty("unit-template-params");
            }

            @Override
            public RoutedPath absolute() {
                return this;
            }

            @Override
            public String rawPath() {
                return path;
            }

            @Override
            public String rawPathNoParams() {
                return path;
            }

            @Override
            public String path() {
                return path;
            }

            @Override
            public Parameters matrixParameters() {
                return Parameters.empty("unit-routed-path");
            }

            @Override
            public void validate() {
            }
        });
        return request;
    }

    static class TestContentHandler extends FileSystemContentHandler {

        final AtomicInteger counter = new AtomicInteger(0);
        final boolean returnValue;
        Path path;

        TestContentHandler(StaticContentSupport.FileSystemBuilder builder, boolean returnValue) {
            super(builder);
            this.returnValue = returnValue;
        }

        static TestContentHandler create(boolean returnValue) {
            return new TestContentHandler(StaticContentSupport.builder(Paths.get(".")), returnValue);
        }

        @Override
        boolean doHandle(Http.Method method, Path path, ServerRequest request, ServerResponse response) {
            this.counter.incrementAndGet();
            this.path = path;
            return returnValue;
        }
    }

    static class TestClassPathContentHandler extends ClassPathContentHandler {

        final AtomicInteger counter = new AtomicInteger(0);
        final boolean returnValue;

        TestClassPathContentHandler(StaticContentSupport.ClassPathBuilder builder, boolean returnValue) {
            super(builder);
            this.returnValue = returnValue;
        }

        static TestClassPathContentHandler create() {
            return new TestClassPathContentHandler(StaticContentSupport.builder("/root"), true);
        }

        @Override
        boolean doHandle(Http.Method method, String path, ServerRequest request, ServerResponse response)
                throws IOException, URISyntaxException {
            super.doHandle(method, path, request, response);
            this.counter.incrementAndGet();
            return returnValue;
        }

    }
}
