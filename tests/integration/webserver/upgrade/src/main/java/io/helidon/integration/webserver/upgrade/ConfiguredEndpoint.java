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

package io.helidon.integration.webserver.upgrade;

import java.io.IOException;
import java.lang.System.Logger.Level;

import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

public class ConfiguredEndpoint extends Endpoint {

    private static final System.Logger LOGGER = System.getLogger(ConfiguredEndpoint.class.getName());

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        LOGGER.log(Level.DEBUG, "SERVER: onOpen " + session.getId());
        send(session, "Hello this is server calling on open!");
        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String s) {
                LOGGER.log(Level.DEBUG, "SERVER: onMessage " + session.getId() + " - " + s);
                send(session, s);
            }
        });

    }

    @Override
    public void onError(Session session, Throwable thr) {
        LOGGER.log(Level.ERROR, "SERVER: onError", thr);
        super.onError(session, thr);
    }

    private void send(Session session, String msg) {
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
