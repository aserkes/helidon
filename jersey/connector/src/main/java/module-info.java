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

import io.helidon.jersey.connector.HelidonConnectorProvider;

/**
 * A {@link org.glassfish.jersey.client.spi.Connector} that utilizes the Helidon HTTP Client to send and receive
 *  * HTTP request and responses.
 */
module io.helidon.jersey.connector {
    requires java.logging;

    requires jakarta.ws.rs;
    requires jersey.client;
    requires jersey.common;
    requires io.helidon.common.reactive;
    requires io.helidon.reactive.webclient;
    requires io.netty.codec.http;

    exports io.helidon.jersey.connector;
    provides org.glassfish.jersey.client.spi.ConnectorProvider with HelidonConnectorProvider;
}
