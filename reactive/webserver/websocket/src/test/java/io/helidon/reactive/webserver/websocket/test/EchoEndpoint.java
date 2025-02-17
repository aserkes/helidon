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
package io.helidon.reactive.webserver.websocket.test;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import static io.helidon.reactive.webserver.websocket.test.UppercaseCodec.isDecoded;

/**
 * Class EchoEndpoint. Only one instance of this endpoint should be used at
 * a time. See static {@code EchoEndpoint#modifyHandshakeCalled}.
 */
@ServerEndpoint(
        value = "/echo",
        encoders = { UppercaseCodec.class },
        decoders = { UppercaseCodec.class },
        configurator = EchoEndpoint.ServerConfigurator.class
)
public class EchoEndpoint {
    private static final System.Logger LOGGER = System.getLogger(EchoEndpoint.class.getName());

    static AtomicBoolean modifyHandshakeCalled = new AtomicBoolean(false);

    /**
     * Verify that endpoint methods are running in a Helidon thread pool.
     *
     * @param session Websocket session.
     * @param logger A logger.
     * @throws IOException Exception during close.
     */
    private static void verifyRunningThread(Session session, System.Logger logger) throws IOException {
        Thread thread = Thread.currentThread();
        if (!thread.getName().contains("EventLoop")) {
            logger.log(Level.WARNING, "Websocket handler running in incorrect thread " + thread);
            session.close();
        }
    }

    /**
     * Verify session includes expected query params.
     *
     * @param session Websocket session.
     * @param logger A logger.
     * @throws IOException Exception during close.
     */
    private static void verifyQueryParams(Session session, System.Logger logger) throws IOException {
        if (!"user=Helidon".equals(session.getQueryString())) {
            logger.log(Level.WARNING, "Websocket session does not include required query params");
            session.close();
        }
        if (!session.getRequestParameterMap().get("user").get(0).equals("Helidon")) {
            logger.log(Level.WARNING, "Websocket session does not include required query parameter map");
            session.close();
        }
    }

    public static class ServerConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            LOGGER.log(Level.INFO, "ServerConfigurator called during handshake");
            super.modifyHandshake(sec, request, response);
            EchoEndpoint.modifyHandshakeCalled.set(true);
        }
    }

    @OnOpen
    public void onOpen(Session session) throws IOException {
        LOGGER.log(Level.INFO, "OnOpen called");
        verifyRunningThread(session, LOGGER);
        verifyQueryParams(session, LOGGER);
        if (!modifyHandshakeCalled.get()) {
            session.close();        // unexpected
        }
    }

    @OnMessage
    public void echo(Session session, String message) throws Exception {
        LOGGER.log(Level.INFO, "Endpoint OnMessage called '" + message + "'");
        verifyRunningThread(session, LOGGER);
        verifyQueryParams(session, LOGGER);
        if (!isDecoded(message)) {
            throw new InternalError("Message has not been decoded");
        }
        session.getBasicRemote().sendObject(message);       // calls encoder
    }

    @OnError
    public void onError(Throwable t) {
        LOGGER.log(Level.INFO, "OnError called");
        modifyHandshakeCalled.set(false);
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        LOGGER.log(Level.INFO, "OnClose called");
        verifyRunningThread(session, LOGGER);
        verifyQueryParams(session, LOGGER);
        modifyHandshakeCalled.set(false);
    }
}
