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

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.FutureListener;

import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.CONNECTION_IDENT;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.IN_USE;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.RECEIVED;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.RESPONSE_RECEIVED;
import static io.helidon.reactive.webclient.WebClientRequestBuilderImpl.RESULT;

/**
 * Helidon Web Client initializer which is used for netty channel initialization.
 */
class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    private final RequestConfiguration configuration;

    /**
     * Creates new instance.
     *
     * @param configuration request configuration
     */
    NettyClientInitializer(RequestConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();

        URI address = configuration.requestURI();

        // read timeout (we also want to timeout waiting on a proxy)
        Duration readTimeout = configuration.readTimout();
        pipeline.addLast("readTimeout", new HelidonReadTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS));

        // proxy configuration
        configuration.proxy()
                .flatMap(proxy -> proxy.handler(address))
                .ifPresent(it -> {
                    ProxyHandler proxyHandler = (ProxyHandler) it;
                    proxyHandler.setConnectTimeoutMillis(configuration.connectTimeout().toMillis());
                    pipeline.addLast(proxyHandler);
                });

        // TLS configuration
        if (address.toString().startsWith("https")) {
            configuration.sslContext().ifPresent(ctx -> {
                SslHandler sslHandler = ctx.newHandler(channel.alloc(), address.getHost(), address.getPort());

                //This is how to enable hostname verification in netty
                if (!configuration.tls().disableHostnameVerification()) {
                    SSLEngine sslEngine = sslHandler.engine();
                    SSLParameters sslParameters = sslEngine.getSSLParameters();
                    sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                    sslEngine.setSSLParameters(sslParameters);
                }

                pipeline.addLast("ssl", sslHandler);
                sslHandler.handshakeFuture().addListener((FutureListener<Channel>) channelFuture -> {
                    //Check if ssl handshake has been successful. Without this check will this exception be replaced by
                    //netty and therefore it will be lost.
                    if (channelFuture.cause() != null) {
                        channel.attr(RESULT).get().completeExceptionally(channelFuture.cause());
                        channel.close();
                    }
                });
            });
        }

        pipeline.addLast("logger", new LoggingHandler(ClientNettyLog.class, LogLevel.TRACE));
        pipeline.addLast("httpCodec", new HttpClientCodec());
        pipeline.addLast("httpDecompressor", new HttpContentDecompressor());
        pipeline.addLast("idleStateHandler", new IdleStateHandler(0, 0, 50));
        pipeline.addLast("idleConnectionHandler", new IdleConnectionHandler());
        pipeline.addLast("helidonHandler", new NettyClientHandler());
    }

    private static class IdleConnectionHandler extends ChannelDuplexHandler {

        private static final System.Logger LOGGER = System.getLogger(IdleConnectionHandler.class.getName());

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                if (ctx.channel().hasAttr(IN_USE)
                        && ctx.channel().attr(IN_USE).get().compareAndSet(false, true)) {
                    ctx.close();
                }
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            if (ctx.channel().hasAttr(CONNECTION_IDENT)) {
                WebClientRequestBuilderImpl.ConnectionIdent key = channel.attr(CONNECTION_IDENT).get();
                LOGGER.log(Level.TRACE, () -> "Channel closed -> " + channel.hashCode());
                if (key != null) {
                    WebClientRequestBuilderImpl.removeChannelFromCache(key, channel);
                }
            }
            if (!channel.attr(RESPONSE_RECEIVED).get()) {
                CompletableFuture<WebClientServiceResponse> responseReceived = channel.attr(RECEIVED).get();
                CompletableFuture<WebClientResponse> responseFuture = channel.attr(RESULT).get();
                WebClientException exception = new WebClientException("Connection reset by the host");
                responseReceived.completeExceptionally(exception);
                responseFuture.completeExceptionally(exception);
            }
            super.channelInactive(ctx);
        }
    }

    // this class is only used to create a log handler in NettyLogHandler, to distinguish from webserver
    private static final class ClientNettyLog {
    }
}
