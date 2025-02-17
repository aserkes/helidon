/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http1;

import java.util.Map;
import java.util.Set;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http1.spi.Http1Upgrader;
import io.helidon.nima.webserver.spi.ServerConnection;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

/**
 * HTTP/1.1 server connection selector.
 */
public class Http1ConnectionSelector implements ServerConnectionSelector {
    private static final String PROTOCOL = " HTTP/1.1\r";

    // HTTP/1.1 connection upgrade providers
    private final Http1Config config;
    private final Map<String, Http1Upgrader> upgradeProviderMap;

    // Creates an instance of HTTP/1.1 server connection selector.
    Http1ConnectionSelector(Http1Config config, Map<String, Http1Upgrader> upgradeProviderMap) {
        this.config = config;
        this.upgradeProviderMap = upgradeProviderMap;
    }

    @Override
    public int bytesToIdentifyConnection() {
        // the request must begin with
        return 0;
    }

    @Override
    public Support supports(BufferData request) {
        // we are looking for first \n, if preceded by \r -> try if ours, otherwise not supported

        /*
        > GET /loom/slow HTTP/1.1
        > Host: localhost:8080
        > User-Agent: curl/7.54.0
        > Accept: * /*
         */

        int lf = request.indexOf(Bytes.LF_BYTE);
        if (lf == -1) {
            // in case we have reached the max prologue length, we just consider this to be HTTP/1.1 so we can send
            // proper error. This means that maxPrologueLength should always be higher than any protocol requirements to
            // identify a connection (e.g. this is the fallback protocol)
            return (request.available() <= config.maxPrologueLength()) ? Support.SUPPORTED : Support.UNSUPPORTED;
        } else {
            return request.readString(lf).endsWith(PROTOCOL) ? Support.SUPPORTED : Support.UNSUPPORTED;
        }
    }

    @Override
    public Set<String> supportedApplicationProtocols() {
        return Set.of("http/1.1");
    }

    @Override
    public ServerConnection connection(ConnectionContext ctx) {
        return new Http1Connection(ctx, config, upgradeProviderMap);
    }

}
