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
package io.helidon.tests.integration.nativeimage.se1;

import java.nio.file.Paths;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.FileSystemWatcher;
import io.helidon.health.checks.HealthChecks;
import io.helidon.logging.common.LogConfig;
import io.helidon.reactive.health.HealthSupport;
import io.helidon.reactive.media.jsonb.JsonbSupport;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.metrics.MetricsSupport;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.WebServer;
import io.helidon.reactive.webserver.staticcontent.StaticContentSupport;
import io.helidon.reactive.webserver.websocket.WebSocketRouting;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.tracing.TracerBuilder;

import jakarta.websocket.server.ServerEndpointConfig;
import org.eclipse.microprofile.health.HealthCheckResponse;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * Main class of this integration test.
 */
public final class Se1Main {
    /**
     * Cannot be instantiated.
     */
    private Se1Main() {
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link io.helidon.reactive.webserver.WebServer} instance
     */
    static WebServer startServer() {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = buildConfig();

        // Get webserver config from the "server" section of application.yaml
        WebServer server = WebServer.builder()
                .routing(createRouting(config))
                .routing(WebSocketRouting.builder()
                        .endpoint("/ws", ServerEndpointConfig.Builder.create(
                                        WebSocketEndpoint.class, "/messages")
                                .build())
                        .build())
                .config(config.get("server"))
                .tracer(TracerBuilder.create(config.get("tracing")).build())
                .addMediaSupport(JsonpSupport.create())
                .addMediaSupport(JsonbSupport.create())
                .printFeatureDetails(true)
                .build();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        server.start()
                .thenAccept(ws -> {
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port() + "/greet");
                    ws.whenShutdown().thenRun(()
                                                      -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });

        // Server threads are not daemon. No need to block. Just react.

        return server;
    }

    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        classpath("se-test.yaml").optional(),
                        file("conf/se.yaml")
                                .changeWatcher(FileSystemWatcher.create())
                                .optional(),
                        classpath("application.yaml"))
                .build();
    }

    /**
     * Creates new {@link io.helidon.reactive.webserver.Routing}.
     *
     * @return routing configured with JSON support, a health check, and a service
     * @param config configuration of this server
     */
    private static Routing createRouting(Config config) {

        MetricsSupport metrics = MetricsSupport.create();
        GreetService greetService = new GreetService(config);
        ColorService colorService = new ColorService(config);
        MockZipkinService zipkinService = new MockZipkinService(Set.of("helidon-reactive-webclient"));
        WebClientService webClientService = new WebClientService(config, zipkinService);
        HealthSupport health = HealthSupport.builder()
                .add(HealthChecks.healthChecks())   // Adds a convenient set of checks
                .addLiveness(() -> HealthCheckResponse.named("custom") // a custom health check
                        .up()
                        .withData("timestamp", System.currentTimeMillis())
                        .build())
                .build();

        return Routing.builder()
                .register("/static/path", StaticContentSupport.create(Paths.get("web")))
                .register("/static/classpath", StaticContentSupport.create("web"))
                .register("/static/jar", StaticContentSupport.create("web-jar"))
                .register(WebSecurity.create(config.get("security")))
                .register(health)                   // Health at "/health"
                .register(metrics)                  // Metrics at "/metrics"
                .register("/greet", greetService)
                .register("/color", colorService)
                .register("/wc", webClientService)
                .register("/zipkin", zipkinService)
                .build();
    }

}
