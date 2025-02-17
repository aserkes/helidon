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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.webserver.spi.Http2SubProtocolSelector;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http1.spi.Http1Upgrader;
import io.helidon.nima.webserver.spi.ServerConnection;

/**
 * HTTP/1.1 to HTTP/2 connection upgrade.
 */
public class Http2Upgrader implements Http1Upgrader {
    private static final byte[] SWITCHING_PROTOCOLS_BYTES = (
            "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Upgrade: h2c\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8);
    private static final HeaderName HTTP2_SETTINGS_HEADER_NAME = Header.create("HTTP2-Settings");
    private static final Base64.Decoder BASE_64_DECODER = Base64.getDecoder();

    private final Http2Config config;
    private final List<Http2SubProtocolSelector> subProtocolProviders;

    /**
     * Creates an instance of HTTP/1.1 to HTTP/2 protocol upgrade.
     */
    Http2Upgrader(Http2Config config, List<Http2SubProtocolSelector> subProtocolProviders) {
        this.config = config;
        this.subProtocolProviders = subProtocolProviders;
    }

    @Override
    public String supportedProtocol() {
        return "h2c";
    }

    @Override
    public ServerConnection upgrade(ConnectionContext ctx,
                                    HttpPrologue prologue,
                                    WritableHeaders<?> headers) {
        Http2Connection connection = new Http2Connection(ctx, config, subProtocolProviders);
        if (headers.contains(HTTP2_SETTINGS_HEADER_NAME)) {
            connection.clientSettings(Http2Settings.create(BufferData.create(BASE_64_DECODER.decode(headers.get(
                    HTTP2_SETTINGS_HEADER_NAME).value().getBytes(StandardCharsets.US_ASCII)))));
        } else {
            throw new RuntimeException("Bad request -> not " + HTTP2_SETTINGS_HEADER_NAME + " header");
        }
        Http2Headers http2Headers = Http2Headers.create(headers);
        http2Headers.path(prologue.uriPath().rawPath());
        http2Headers.method(prologue.method());
        headers.remove(Header.HOST,
                       it -> http2Headers.authority(it.value()));
        http2Headers.scheme("http"); // TODO need to get if https (ctx)?

        HttpPrologue newPrologue = HttpPrologue.create(Http2Connection.FULL_PROTOCOL,
                                                       prologue.protocol(),
                                                       Http2Connection.PROTOCOL_VERSION,
                                                       prologue.method(),
                                                       prologue.uriPath(),
                                                       prologue.query(),
                                                       prologue.fragment());

        connection.upgradeConnectionData(newPrologue, http2Headers);
        connection.expectPreface();
        DataWriter dataWriter = ctx.dataWriter();
        dataWriter.write(BufferData.create(SWITCHING_PROTOCOLS_BYTES));
        return connection;
    }

}
