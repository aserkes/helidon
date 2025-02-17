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

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.LazyValue;
import io.helidon.common.Version;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.reactive.media.common.MediaContext;

import io.netty.channel.nio.NioEventLoopGroup;

/*
 * This class must be:
 *   - thread safe
 *   - graalVm native-image safe (e.g. you must be able to store this class statically)
 *       - what about the base URI? only would work with prod config
 */
final class NettyClient implements WebClient {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(10);
    private static final boolean DEFAULT_FOLLOW_REDIRECTS = false;
    private static final boolean DEFAULT_KEEP_ALIVE = true;
    private static final boolean DEFAULT_VALIDATE_HEADERS = true;
    private static final int DEFAULT_NUMBER_OF_REDIRECTS = 5;
    private static final LazyValue<String> DEFAULT_USER_AGENT = LazyValue
            .create(() -> "Helidon/" + Version.VERSION + " (java " + System.getProperty("java.runtime.version") + ")");
    private static final Proxy DEFAULT_PROXY = Proxy.noProxy();
    private static final MediaContext DEFAULT_MEDIA_SUPPORT = MediaContext.create();
    private static final WebClientTls DEFAULT_TLS = WebClientTls.builder().build();

    private static final DnsResolverType DEFAULT_DNS_RESOLVER_TYPE = DnsResolverType.DEFAULT;

    private static final Config GLOBAL_CLIENT_CONFIG;

    // configurable per client instance
    static final WebClientConfiguration SHARED_CONFIGURATION;

    static {
        Config globalConfig = Contexts.globalContext().get(Config.class).orElseGet(Config::empty);

        GLOBAL_CLIENT_CONFIG = globalConfig.get("client");

        SHARED_CONFIGURATION = WebClientConfiguration.builder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .readTimeout(DEFAULT_READ_TIMEOUT)
                .followRedirects(DEFAULT_FOLLOW_REDIRECTS)
                .maxRedirects(DEFAULT_NUMBER_OF_REDIRECTS)
                .userAgent(DEFAULT_USER_AGENT)
                .readerContextParent(DEFAULT_MEDIA_SUPPORT.readerContext())
                .writerContextParent(DEFAULT_MEDIA_SUPPORT.writerContext())
                .proxy(DEFAULT_PROXY)
                .tls(DEFAULT_TLS)
                .keepAlive(DEFAULT_KEEP_ALIVE)
                .validateHeaders(DEFAULT_VALIDATE_HEADERS)
                .dnsResolverType(DEFAULT_DNS_RESOLVER_TYPE)
                .config(GLOBAL_CLIENT_CONFIG)
                .build();
    }

    // shared by all client instances
    private static final LazyValue<NioEventLoopGroup> EVENT_GROUP = LazyValue.create(() -> {
        Config eventLoopConfig = GLOBAL_CLIENT_CONFIG.get("event-loop");
        int numberOfThreads = eventLoopConfig.get("workers")
                .asInt()
                .orElse(1);
        String threadNamePrefix = eventLoopConfig.get("name-prefix")
                .asString()
                .orElse("helidon-client-");
        AtomicInteger threadCounter = new AtomicInteger();

        ThreadFactory threadFactory =
                r -> {
                    Thread result = new Thread(r, threadNamePrefix + threadCounter.getAndIncrement());
                    // we should exit the VM if client event loop is the only thread(s) running
                    result.setDaemon(true);
                    return result;
                };

        ExecutorService executorService = Executors.newCachedThreadPool(threadFactory);

        return new NioEventLoopGroup(numberOfThreads, Contexts.wrap(executorService));
    });

    // this instance configuration
    private final WebClientConfiguration configuration;

    /**
     * Creates new instance.
     *
     * @param builder client builder
     */
    NettyClient(Builder builder) {
        this.configuration = builder.configuration();

        // we need to configure these - if user wants to override, they must
        // do it before first usage
//        configureDefaults(EMPTY_CONFIG);
    }

    static NioEventLoopGroup eventGroup() {
        return EVENT_GROUP.get();
    }

    @Override
    public WebClientRequestBuilder put() {
        return method(Http.Method.PUT);
    }

    @Override
    public WebClientRequestBuilder get() {
        return method(Http.Method.GET);
    }

    @Override
    public WebClientRequestBuilder post() {
        return method(Http.Method.POST);
    }

    @Override
    public WebClientRequestBuilder delete() {
        return method(Http.Method.DELETE);
    }

    @Override
    public WebClientRequestBuilder options() {
        return method(Http.Method.OPTIONS);
    }

    @Override
    public WebClientRequestBuilder trace() {
        return method(Http.Method.TRACE);
    }

    @Override
    public WebClientRequestBuilder head() {
        return method(Http.Method.HEAD);
    }

    @Override
    public WebClientRequestBuilder method(String method) {
        return WebClientRequestBuilderImpl.create(EVENT_GROUP.get(), configuration, Http.Method.create(method));
    }

    @Override
    public WebClientRequestBuilder method(Http.Method method) {
        return WebClientRequestBuilderImpl.create(EVENT_GROUP.get(), configuration, method);
    }

}
