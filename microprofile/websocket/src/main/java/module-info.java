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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * MP Tyrus Integration
 */
@Feature(value = "Websocket",
        description = "Jakarta Websocket implementation",
        in = HelidonFlavor.MP,
        path = "Websocket"
)
@Aot(false)
module io.helidon.microprofile.tyrus {
    requires static io.helidon.common.features.api;

    requires java.net.http;
    requires jakarta.inject;

    requires jakarta.cdi;
    requires transitive jakarta.websocket;

    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.microprofile.cdi;
    requires io.helidon.microprofile.server;
    requires io.helidon.nima.webserver;
    requires io.helidon.nima.websocket.webserver;

    requires org.glassfish.tyrus.core;
    requires org.glassfish.tyrus.server;
    requires org.glassfish.tyrus.spi;

    exports io.helidon.microprofile.tyrus;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.tyrus to weld.core.impl, io.helidon.microprofile.cdi;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.tyrus.TyrusCdiExtension;
    provides org.glassfish.tyrus.core.ComponentProvider
            with io.helidon.microprofile.tyrus.HelidonComponentProvider;
    provides io.helidon.nima.webserver.http1.spi.Http1UpgradeProvider
            with io.helidon.microprofile.tyrus.TyrusUpgradeProvider;
}
