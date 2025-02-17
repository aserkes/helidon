/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

/**
 * OIDC common classes.
 */
module io.helidon.security.providers.oidc.common {

    // EncryptionProvider.EncryptionSupport is part of API
    requires transitive io.helidon.security;
    // TokenHandler is part of API
    requires transitive io.helidon.security.util;
    // WebClient is part of API
    requires transitive io.helidon.reactive.webclient;
    requires io.helidon.common.parameters;

    requires io.helidon.security.providers.common;
    requires io.helidon.security.jwt;
    requires io.helidon.security.providers.httpauth;
    requires io.helidon.reactive.webclient.jaxrs;
    requires io.helidon.reactive.webclient.security;
    requires io.helidon.reactive.webclient.tracing;
    requires io.helidon.reactive.media.jsonp;
    requires io.helidon.common.crypto;
    requires static io.helidon.config.metadata;
    requires io.helidon.cors;

    // these are deprecated and will be removed in 3.x
    requires jersey.client;
    requires jakarta.ws.rs;

    exports io.helidon.security.providers.oidc.common;
    exports io.helidon.security.providers.oidc.common.spi;
}
