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

package io.helidon.nima.http2.webserver;

import java.util.List;
import java.util.Set;

import io.helidon.common.buffers.BufferData;
import io.helidon.nima.http2.webserver.spi.Http2SubProtocolSelector;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.spi.ServerConnection;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

import static io.helidon.nima.http2.Http2Util.PREFACE_LENGTH;
import static io.helidon.nima.http2.Http2Util.isPreface;

/**
 * HTTP/2 server connection selector.
 */
public class Http2ConnectionSelector implements ServerConnectionSelector {

    private final Http2Config http2Config;
    private final List<Http2SubProtocolSelector> subProviders;

    // Creates an instance of HTTP/2 server connection selector.
    Http2ConnectionSelector(Http2Config http2Config, List<Http2SubProtocolSelector> subProviders) {
        this.http2Config = http2Config;
        this.subProviders = subProviders;
    }

    @Override
    public int bytesToIdentifyConnection() {
        return PREFACE_LENGTH;
    }

    @Override
    public Support supports(BufferData request) {
        byte[] prefaceBytes = new byte[PREFACE_LENGTH];
        request.read(prefaceBytes, 0, PREFACE_LENGTH);

        // now we can ask protocol handler to identify this protocol
        if (isPreface(prefaceBytes)) {
            // this is HTTP/2 prior knowledge
            return Support.SUPPORTED;
        }

        return Support.UNSUPPORTED;
    }

    @Override
    public Set<String> supportedApplicationProtocols() {
        return Set.of("h2");
    }

    @Override
    public ServerConnection connection(ConnectionContext ctx) {
        Http2Connection result = new Http2Connection(ctx, http2Config, subProviders);
        result.expectPreface();

        return result;
    }

}
