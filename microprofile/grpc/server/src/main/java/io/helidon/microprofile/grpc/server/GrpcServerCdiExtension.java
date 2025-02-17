/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.server;

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.GrpcService;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.InProcessGrpcChannel;
import io.helidon.microprofile.grpc.server.spi.GrpcMpContext;
import io.helidon.microprofile.grpc.server.spi.GrpcMpExtension;

import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * A CDI extension that will start the {@link GrpcServer gRPC server}.
 * <p>
 * The server is started when the {@link AfterDeploymentValidation} event
 * is received and will be stopped when the {@link BeforeShutdown} event
 * is received.
 * <p>
 * If no gRPC services are discovered the gRPC server will not be started.
 */
public class GrpcServerCdiExtension
        implements Extension {

    private static final System.Logger LOGGER = System.getLogger(GrpcServerCdiExtension.class.getName());
    private static final System.Logger STARTUP_LOGGER = System.getLogger("io.helidon.microprofile.startup.server");

    private GrpcServer server;


    private void startServer(@Observes @Initialized(ApplicationScoped.class) Object event, BeanManager beanManager) {
        GrpcRouting.Builder routingBuilder = discoverGrpcRouting(beanManager);

        Config config = MpConfig.toHelidonConfig(ConfigProvider.getConfig());
        GrpcServerConfiguration.Builder serverConfiguration = GrpcServerConfiguration.builder(config.get("grpc"));
        CompletableFuture<GrpcServer> startedFuture = new CompletableFuture<>();
        CompletableFuture<GrpcServer> shutdownFuture = new CompletableFuture<>();

        loadExtensions(beanManager, config, routingBuilder, serverConfiguration, startedFuture, shutdownFuture);
        server = GrpcServer.create(serverConfiguration.build(), routingBuilder.build());
        long beforeT = System.nanoTime();

        server.start()
                .whenComplete((grpcServer, throwable) -> {
                    if (null != throwable) {
                        STARTUP_LOGGER.log(Level.ERROR, () -> "gRPC server startup failed", throwable);
                        startedFuture.completeExceptionally(throwable);
                    } else {
                        long t = TimeUnit.MILLISECONDS.convert(System.nanoTime() - beforeT, TimeUnit.NANOSECONDS);

                        int port = grpcServer.port();
                        STARTUP_LOGGER.log(Level.TRACE, "gRPC server started up");
                        LOGGER.log(Level.INFO, () -> "gRPC server started on localhost:"
                                + port + " (and all other host addresses) "
                                + "in " + t + " milliseconds.");

                        grpcServer.whenShutdown()
                                .whenComplete((server, error) -> {
                                    if (error == null) {
                                        shutdownFuture.complete(server);
                                    } else {
                                        shutdownFuture.completeExceptionally(error);
                                    }
                                });

                        startedFuture.complete(grpcServer);
                    }
                });

        // inject the server into the producer so that it can be discovered later
        ServerProducer serverProducer = beanManager.createInstance().select(ServerProducer.class).get();
        serverProducer.server(server);
    }

    private void stopServer(@Observes BeforeShutdown event) {
        if (server != null) {
            LOGGER.log(Level.INFO, "Stopping gRPC server");
            long beforeT = System.nanoTime();
            server.shutdown()
                  .whenComplete((webServer, throwable) -> {
                      if (null != throwable) {
                          LOGGER.log(Level.ERROR, () -> "An error occurred stopping the gRPC server", throwable);
                      } else {
                          long t = TimeUnit.MILLISECONDS.convert(System.nanoTime() - beforeT, TimeUnit.NANOSECONDS);
                          LOGGER.log(Level.INFO, () -> "gRPC Server stopped in " + t + " milliseconds.");
                      }
                  });
        }
    }

    /**
     * Discover the services and interceptors to use to configure the
     * {@link GrpcRouting}.
     *
     * @param beanManager  the CDI bean manager
     * @return the {@link GrpcRouting} to use or {@code null} if no services
     *         or routing were discovered
     */
    private GrpcRouting.Builder discoverGrpcRouting(BeanManager beanManager) {
        Instance<Object> instance = beanManager.createInstance();
        GrpcRouting.Builder builder = GrpcRouting.builder();

        // discover @Grpc annotated beans
        // we use the bean manager to do this as we need the actual bean class
        beanManager.getBeans(Object.class, Any.Literal.INSTANCE)
                .stream()
                .filter(this::hasGrpcQualifier)
                .forEach(bean -> {
                    Class<?> beanClass = bean.getBeanClass();
                    Annotation[] qualifiers = bean.getQualifiers().toArray(new Annotation[0]);
                    Object service = instance.select(beanClass, qualifiers).get();
                    register(service, builder, beanClass, beanManager);
                });

        // discover beans of type GrpcService
        beanManager.getBeans(GrpcService.class)
                .forEach(bean -> {
                    Class<?> beanClass = bean.getBeanClass();
                    Annotation[] qualifiers = bean.getQualifiers().toArray(new Annotation[0]);
                    Object service = instance.select(beanClass, qualifiers).get();
                    builder.register((GrpcService) service);
                });

        // discover beans of type BindableService
        beanManager.getBeans(BindableService.class)
                .forEach(bean -> {
                    Class<?> beanClass = bean.getBeanClass();
                    Annotation[] qualifiers = bean.getQualifiers().toArray(new Annotation[0]);
                    Object service = instance.select(beanClass, qualifiers).get();
                    builder.register((BindableService) service);
                });

        return builder;
    }

    private boolean hasGrpcQualifier(Bean<?> bean) {
        return bean.getQualifiers()
                .stream()
                .anyMatch(q -> Grpc.class.isAssignableFrom(q.annotationType()));
    }

    /**
     * Load any instances of {@link GrpcMpExtension} discovered by the
     * {@link ServiceLoader} and allow them to further configure the
     * gRPC server.
     *
     * @param beanManager the {@link BeanManager}
     * @param config the Helidon configuration
     * @param routingBuilder the {@link GrpcRouting.Builder}
     * @param serverConfiguration the {@link GrpcServerConfiguration}
     */
    private void loadExtensions(BeanManager beanManager,
                                Config config,
                                GrpcRouting.Builder routingBuilder,
                                GrpcServerConfiguration.Builder serverConfiguration,
                                CompletionStage<GrpcServer> whenStarted,
                                CompletionStage<GrpcServer> whenShutdown) {

        GrpcMpContext context = new GrpcMpContext() {
            @Override
            public Config config() {
                return config;
            }

            @Override
            public GrpcServerConfiguration.Builder grpcServerConfiguration() {
                return serverConfiguration;
            }

            @Override
            public GrpcRouting.Builder routing() {
                return routingBuilder;
            }

            @Override
            public BeanManager beanManager() {
                return beanManager;
            }

            @Override
            public CompletionStage<GrpcServer> whenStarted() {
                return whenStarted;
            }

            @Override
            public CompletionStage<GrpcServer> whenShutdown() {
                return whenShutdown;
            }
        };

        HelidonServiceLoader.create(ServiceLoader.load(GrpcMpExtension.class))
                .forEach(ext -> ext.configure(context));

        beanManager.createInstance()
                .select(GrpcMpExtension.class)
                .stream()
                .forEach(ext -> ext.configure(context));
    }

    /**
     * Register the service with the routing.
     * <p>
     * The service is actually a CDI proxy so the real service.
     *
     * @param service the service to register
     * @param builder the gRPC routing
     * @param beanManager the {@link BeanManager} to use to locate beans required by the service
     */
    private void register(Object service, GrpcRouting.Builder builder, Class<?> cls, BeanManager beanManager) {
        GrpcServiceBuilder serviceBuilder = GrpcServiceBuilder.create(cls, () -> service, beanManager);
        if (serviceBuilder.isAnnotatedService()) {
            builder.register(serviceBuilder.build());
        } else {
            LOGGER.log(Level.WARNING,
                       () -> "Discovered type is not a properly annotated gRPC service " + service.getClass());
        }
    }

    /**
     * A CDI producer that can supply the running {@link GrpcServer}
     * an in-process {@link Channel}.
     */
    @ApplicationScoped
    public static class ServerProducer {

        private GrpcServer server;

        /**
         * Produce the {@link GrpcServer}.
         *
         * @return the {@link GrpcServer}
         */
        @Produces
        public GrpcServer server() {
            return server;
        }

        /**
         * Produce a {@link Supplier} that can supply the {@link GrpcServer}.
         * <p>
         * This could be useful where an injection point has the server injected
         * before the {@link #startServer} method has actually started it. In that
         * case a {@link Supplier Supplier&lt;GrpcServer&gt;} can be injected instead
         * that will be able to lazily supply the server.
         *
         * @return a {@link Supplier} that can supply the {@link GrpcServer}
         */
        @Produces
        public Supplier<GrpcServer> supply() {
            return this::server;
        }

        /**
         * Produces an in-process {@link Channel} to connect to the
         * running gRPC server.
         *
         * @return an in-process {@link Channel} to connect to the
         *         running gRPC server
         */
        @Produces
        @InProcessGrpcChannel
        public Channel channel() {
            String name = server.configuration().name();
            return InProcessChannelBuilder.forName(name)
                    .usePlaintext()
                    .build();
        }

        /**
         * Produces an in-process {@link InProcessChannelBuilder} to
         * connect to the running gRPC server.
         *
         * @return an in-process {@link InProcessChannelBuilder} to
         *         connect to the running gRPC server
         */
        @Produces
        @InProcessGrpcChannel
        public InProcessChannelBuilder channelBuilder() {
            String name = server.configuration().name();
            return InProcessChannelBuilder.forName(name);
        }

        void server(GrpcServer server) {
            this.server = server;
        }
    }
}
