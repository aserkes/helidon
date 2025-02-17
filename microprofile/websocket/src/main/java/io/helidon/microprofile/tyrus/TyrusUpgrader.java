/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.RequestException;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.spi.ServerConnection;
import io.helidon.nima.websocket.webserver.WsUpgrader;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.WebSocketEngine;

/**
 * Tyrus connection upgrade provider.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 100)      // higher than base class
public class TyrusUpgrader extends WsUpgrader {
    private static final System.Logger LOGGER = System.getLogger(TyrusUpgrader.class.getName());

    // there is a single instance of an upgrader used by all connections, do not store request related information in a field
    private final TyrusRouting tyrusRouting;
    private final WebSocketEngine engine;

    TyrusUpgrader(Set<String> origins) {
        super(origins);
        TyrusCdiExtension extension = CDI.current().select(TyrusCdiExtension.class).get();
        Objects.requireNonNull(extension);
        this.tyrusRouting = extension.tyrusRouting();
        TyrusServerContainer tyrusServerContainer = initializeTyrus();
        this.engine = tyrusServerContainer.getWebSocketEngine();
    }


    @Override
    public ServerConnection upgrade(ConnectionContext ctx, HttpPrologue prologue, WritableHeaders<?> headers) {
        // Check required header
        String wsKey;
        if (headers.contains(WS_KEY)) {
            wsKey = headers.get(WS_KEY).value();
        } else {
            // this header is required
            return null;
        }

        // Verify protocol version
        String version;
        if (headers.contains(WS_VERSION)) {
            version = headers.get(WS_VERSION).value();
        } else {
            version = SUPPORTED_VERSION;
        }
        if (!SUPPORTED_VERSION.equals(version)) {
            throw RequestException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("Unsupported WebSocket Version")
                    .header(SUPPORTED_VERSION_HEADER)
                    .build();
        }

        // Initialize path and queryString
        String path = prologue.uriPath().path();
        UriQuery query = prologue.query();

        // Check if this a Tyrus route exists
        TyrusRoute route = ctx.router()
                .routing(TyrusRouting.class, tyrusRouting)
                .findRoute(prologue);
        if (route == null) {
            return null;
        }

        // Validate origin
        if (!anyOrigin()) {
            if (headers.contains(Http.Header.ORIGIN)) {
                String origin = headers.get(Http.Header.ORIGIN).value();
                if (!origins().contains(origin)) {
                    throw RequestException.builder()
                            .message("Invalid Origin")
                            .type(DirectHandler.EventType.FORBIDDEN)
                            .build();
                }
            }
        }

        // Protocol handshake with Tyrus
        WebSocketEngine.UpgradeInfo upgradeInfo = protocolHandshake(headers, query, path);

        // todo support subprotocols (must be provided by route)
        // Sec-WebSocket-Protocol: sub-protocol (list provided in PROTOCOL header, separated by comma space
        DataWriter dataWriter = ctx.dataWriter();
        String switchingProtocols = SWITCHING_PROTOCOL_PREFIX + hash(ctx, wsKey) + SWITCHING_PROTOCOLS_SUFFIX;
        dataWriter.write(BufferData.create(switchingProtocols.getBytes(StandardCharsets.US_ASCII)));

        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Upgraded to websocket version " + version);
        }
        return new TyrusConnection(ctx, upgradeInfo);
    }

    TyrusServerContainer initializeTyrus() {
        Set<Class<?>> allEndpointClasses = tyrusRouting.routes()
                .stream()
                .map(TyrusRoute::endpointClass)
                .collect(Collectors.toSet());

        TyrusServerContainer tyrusServerContainer = new TyrusServerContainer(allEndpointClasses) {
            private final WebSocketEngine engine =
                    TyrusWebSocketEngine.builder(this).build();

            @Override
            public void register(Class<?> endpointClass) {
                throw new UnsupportedOperationException("Cannot register endpoint class");
            }

            @Override
            public void register(ServerEndpointConfig serverEndpointConfig) {
                throw new UnsupportedOperationException("Cannot register ServerEndpointConfig");
            }

            @Override
            public Set<Extension> getInstalledExtensions() {
                return tyrusRouting.extensions();
            }

            @Override
            public WebSocketEngine getWebSocketEngine() {
                return engine;
            }
        };

        // Register classes with context path "/"
        WebSocketEngine engine = tyrusServerContainer.getWebSocketEngine();
        tyrusRouting.routes().forEach(route -> {
            try {
                if (route.serverEndpointConfig() != null) {
                    LOGGER.log(Level.DEBUG, () -> "Registering ws endpoint "
                            + route.path()
                            + route.serverEndpointConfig().getPath());
                    engine.register(route.serverEndpointConfig(), route.path());
                } else {
                    LOGGER.log(Level.DEBUG, () -> "Registering annotated ws endpoint " + route.path());
                    engine.register(route.endpointClass(), route.path());
                }
            } catch (DeploymentException e) {
                throw new RuntimeException(e);
            }
        });

        return tyrusServerContainer;
    }

    WebSocketEngine.UpgradeInfo protocolHandshake(WritableHeaders<?> headers, UriQuery uriQuery, String path) {
        LOGGER.log(Level.DEBUG, "Initiating WebSocket handshake with Tyrus...");

        // Create Tyrus request context, copy request headers and query params
        Map<String, String[]> paramsMap = new HashMap<>();

        for (String name : uriQuery.names()) {
            paramsMap.put(name, uriQuery.all(name).toArray(new String[0]));
        }
        RequestContext requestContext = RequestContext.Builder.create()
                .requestURI(URI.create(path))      // excludes context path
                .queryString(uriQuery.value())
                .parameterMap(paramsMap)
                .build();
        headers.forEach(e -> requestContext.getHeaders().put(e.name(), List.of(e.values())));

        // Use Tyrus to process a WebSocket upgrade request
        final TyrusUpgradeResponse upgradeResponse = new TyrusUpgradeResponse();
        final WebSocketEngine.UpgradeInfo upgradeInfo = engine.upgrade(requestContext, upgradeResponse);

        // Map Tyrus response headers back to Nima
        upgradeResponse.getHeaders()
                .forEach((key, value) -> headers.add(
                        Http.Header.create(
                                Http.Header.createName(key, key.toLowerCase(Locale.ROOT)),
                                value)));
        return upgradeInfo;
    }

}
