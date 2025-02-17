/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.reactive.webclient;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Single;
import io.helidon.reactive.webclient.spi.WebClientService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;

import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.COMPLETED;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.IN_USE;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.RECEIVED;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.REQUEST;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.REQUEST_ID;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.RESPONSE;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.RESPONSE_RECEIVED;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.RESULT;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.RETURN;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.WILL_CLOSE;

/**
 * Created for each request/response interaction.
 */
class NettyClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final System.Logger LOGGER = System.getLogger(NettyClientHandler.class.getName());

    private static final AttributeKey<WebClientServiceResponse> SERVICE_RESPONSE = AttributeKey.valueOf("serviceResponse");
    /**
     * Instance of the publisher used to handle response.
     */
    static final AttributeKey<BufferedEmittingPublisher> PUBLISHER = AttributeKey.valueOf("publisher");

    private static final List<HttpInterceptor> HTTP_INTERCEPTORS = new ArrayList<>();

    static {
        HTTP_INTERCEPTORS.add(new RedirectInterceptor());
    }

    private HttpResponsePublisher publisher;
    private ResponseCloser responseCloser;
    private long requestId;

    /**
     * Creates new instance.
     */
    NettyClientHandler() {
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        ctx.flush();
        if (publisher != null && publisher.hasRequests()) {
            channel.read();
        }
        if (!channel.attr(WILL_CLOSE).get()
                && channel.hasAttr(RETURN)
                && channel.attr(RETURN).get().compareAndSet(true, false)) {
            LOGGER.log(Level.TRACE, () -> "(client reqID: " + requestId + ") "
                    + "Returning channel " + channel.hashCode() + " to the cache");
            channel.attr(IN_USE).get().set(false);
            responseCloser.cf.complete(null);
            publisher.complete();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws IOException {
        Channel channel = ctx.channel();
        if (msg instanceof HttpResponse) {
            channel.config().setAutoRead(false);
            HttpResponse response = (HttpResponse) msg;
            this.requestId = channel.attr(REQUEST_ID).get();
            channel.attr(RESPONSE_RECEIVED).set(true);
            WebClientRequestImpl clientRequest = channel.attr(REQUEST).get();
            RequestConfiguration requestConfiguration = clientRequest.configuration();
            LOGGER.log(Level.TRACE, () -> "(client reqID: " + requestId + ") Initial http response message received");

            this.publisher = new HttpResponsePublisher(ctx);
            channel.attr(PUBLISHER).set(this.publisher);
            this.responseCloser = new ResponseCloser(ctx);
            WebClientResponseImpl.Builder responseBuilder = WebClientResponseImpl.builder();
            responseBuilder.contentPublisher(publisher)
                    .readerContext(requestConfiguration.readerContext())
                    .status(helidonStatus(response.status()))
                    .httpVersion(Http.Version.create(response.protocolVersion().toString()))
                    .responseCloser(responseCloser)
                    .lastEndpointURI(requestConfiguration.requestURI());

            HttpHeaders nettyHeaders = response.headers();
            for (String name : nettyHeaders.names()) {
                List<String> values = nettyHeaders.getAll(name);
                responseBuilder.addHeader(name, values);
            }

            String connection = nettyHeaders.get(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE.toString());
            if (connection.equals(HttpHeaderValues.CLOSE.toString())) {
                ctx.channel().attr(WILL_CLOSE).set(true);
            }

            // we got a response, we can safely complete the future
            // all errors are now fed only to the publisher
            WebClientResponse clientResponse = responseBuilder.build();
            channel.attr(RESPONSE).set(clientResponse);

            Map<String, List<String>> cookieMap = new HashMap<>();
            WebClientResponseHeaders responseHeaders = clientResponse.headers();

            if (responseHeaders.contains(Http.Header.SET_COOKIE)) {
                cookieMap.put(Http.Header.SET_COOKIE.defaultCase(), responseHeaders.get(Http.Header.SET_COOKIE).allValues());
            }
            if (responseHeaders.contains(Http.Header.SET_COOKIE2)) {
                cookieMap.put(Http.Header.SET_COOKIE2.defaultCase(), responseHeaders.get(Http.Header.SET_COOKIE2).allValues());
            }
            requestConfiguration.cookieManager().put(requestConfiguration.requestURI(),
                                                     cookieMap);

            for (HttpInterceptor interceptor : HTTP_INTERCEPTORS) {
                if (interceptor.shouldIntercept(response.status(), requestConfiguration)) {
                    boolean continueAfter = !interceptor.continueAfterInterception();
                    if (continueAfter) {
                        responseCloser.close().thenAccept(future -> LOGGER.log(Level.TRACE,
                                () -> "Response closed due to redirection"));
                    }
                    interceptor.handleInterception(response, clientRequest, channel.attr(RESULT).get());
                    if (continueAfter) {
                        return;
                    }
                }
            }

            WebClientServiceResponse clientServiceResponse =
                    new WebClientServiceResponseImpl(requestConfiguration.context().get(),
                                                     responseHeaders,
                                                     clientResponse.status());

            channel.attr(SERVICE_RESPONSE).set(clientServiceResponse);

            List<WebClientService> services = requestConfiguration.services();
            CompletionStage<WebClientServiceResponse> csr = CompletableFuture.completedFuture(clientServiceResponse);

            for (WebClientService service : services) {
                csr = csr.thenCompose(clientSerResponse -> service.response(clientRequest, clientSerResponse));
            }

            CompletableFuture<WebClientServiceResponse> responseReceived = channel.attr(RECEIVED).get();
            CompletableFuture<WebClientResponse> responseFuture = channel.attr(RESULT).get();
            csr.whenComplete((clientSerResponse, throwable) -> {
                if (throwable != null) {
                    responseReceived.completeExceptionally(throwable);
                    responseFuture.completeExceptionally(throwable);
                    responseCloser.close();
                } else {
                    responseReceived.complete(clientServiceResponse);
                    responseReceived.thenRun(() -> {
                        if (shouldResponseAutomaticallyClose(clientResponse)) {
                            responseCloser.close()
                                    .thenAccept(aVoid -> {
                                        LOGGER.log(Level.TRACE, () -> "Response automatically closed. No entity expected");
                                    });
                        }
                        responseFuture.complete(clientResponse);
                    }).exceptionally(t -> {
                        responseFuture.completeExceptionally(t);
                        responseCloser.close();
                        return null;
                    });
                }
            });
        }

        if (responseCloser.isClosed()) {
            if (!channel.attr(WILL_CLOSE).get() && channel.hasAttr(RETURN)) {
                if (msg instanceof LastHttpContent) {
                    LOGGER.log(Level.TRACE, () -> "(client reqID: " + requestId + ") Draining finished");
                    if (channel.isActive()) {
                        channel.attr(RETURN).get().set(true);
                    }
                } else {
                    LOGGER.log(Level.TRACE,
                            () -> "(client reqID: " + requestId + ") Draining not finished, requesting new chunk");
                }
                channel.read();
            }
            return;
        }

        // never "else-if" - msg may be an instance of more than one type, we must process all of them
        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            publisher.emit(content.content());
        }

        if (msg instanceof LastHttpContent) {
            LOGGER.log(Level.TRACE, () -> "(client reqID: " + requestId + ") Last http content received");
            if (channel.hasAttr(RETURN)) {
                channel.attr(RETURN).get().set(true);
                responseCloser.close();
                channel.read();
            } else {
                responseCloser.close();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        // Connection closed without last HTTP content received. Some server problem
        // so we need to fail the publisher and report an exception.
        if (publisher != null && !responseCloser.isClosed()) {
            WebClientException exception = new WebClientException("Connection reset by the host");
            publisher.fail(exception);
        }
    }

    private boolean shouldResponseAutomaticallyClose(WebClientResponse clientResponse) {
        WebClientResponseHeaders headers = clientResponse.headers();
        if (clientResponse.status() == Http.Status.NO_CONTENT_204) {
            return true;
        }
        return headers.contentType().isEmpty()
                && noContentLength(headers)
                && notChunked(headers);
    }

    private boolean noContentLength(WebClientResponseHeaders headers) {
        long l = headers.contentLength().orElse(0);

        return l == 0;
    }

    private boolean notChunked(WebClientResponseHeaders headers) {
        return !headers.transferEncoding().contains("chunked");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        CompletableFuture<WebClientResponse> responseFuture = ctx.channel().attr(RESULT).get();
        if (responseFuture.isDone()) {
            // we failed during entity processing, or during connecting to the remote site
            if (publisher != null) {
                publisher.fail(cause);
            }
        } else {
            // we failed before getting response
            responseFuture.completeExceptionally(cause);
        }
        ctx.close();
    }

    private Http.Status helidonStatus(HttpResponseStatus nettyStatus) {
        return Http.Status.create(nettyStatus.code(), nettyStatus.reasonPhrase());
    }

    private static final class HttpResponsePublisher extends BufferedEmittingPublisher<DataChunk> {

        private final ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();

        HttpResponsePublisher(ChannelHandlerContext ctx) {
            super.onRequest((n, cnt) -> {
                ctx.channel().config().setAutoRead(super.isUnbounded());

                try {
                    lock.lock();
                    if (super.hasRequests()) {
                        ctx.channel().read();
                    }
                } finally {
                    lock.unlock();
                }
            });
        }



        public void emit(final ByteBuf buf) {
            buf.retain();
            super.emit(DataChunk.create(false, true, buf::release,
                       buf.nioBuffer().asReadOnlyBuffer()));
        }
    }

    final class ResponseCloser {

        private final AtomicBoolean closed;
        private final ChannelHandlerContext ctx;
        private final CompletableFuture<Void> cf;

        ResponseCloser(ChannelHandlerContext ctx) {
            this.ctx = ctx;
            this.closed = new AtomicBoolean();
            this.cf = new CompletableFuture<>();
        }

        boolean isClosed() {
            return closed.get();
        }

        /**
         * Asynchronous close method.
         *
         * @return single of the closing process
         */
        Single<Void> close() {
            if (closed.compareAndSet(false, true)) {
                LOGGER.log(Level.TRACE, () -> "(client reqID: " + requestId + ") Closing the response from the server");
                Channel channel = ctx.channel();
                WebClientServiceResponse clientServiceResponse = channel.attr(SERVICE_RESPONSE).get();
                CompletableFuture<WebClientServiceResponse> requestComplete = channel.attr(COMPLETED).get();
                requestComplete.complete(clientServiceResponse);
                if (channel.attr(WILL_CLOSE).get() || !channel.hasAttr(RETURN)) {
                    ctx.close()
                            .addListener(future -> {
                                if (future.isSuccess()) {
                                    LOGGER.log(Level.TRACE,
                                            () -> "(client reqID: " + requestId + ") Response from the server has been closed");
                                    cf.complete(null);
                                } else {
                                    LOGGER.log(Level.ERROR,
                                            () -> "An exception occurred while closing the response",
                                            future.cause());
                                    cf.completeExceptionally(future.cause());
                                }
                            });
                    publisher.complete();
                } else if (!channel.attr(RETURN).get().get()) {
                    LOGGER.log(Level.TRACE, () -> "(client reqID: " + requestId + ") Drain possible remaining entity parts");
                    channel.read();
                }
            }
            return Single.create(cf, true);
        }

    }

}
