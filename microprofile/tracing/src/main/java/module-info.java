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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Eclipse Microprofile Tracing implementation for helidon microprofile.
 *
 * @see org.eclipse.microprofile.opentracing
 */
@Feature(value = "Tracing",
        description = "MicroProfile tracing spec implementation",
        in = HelidonFlavor.MP,
        path = "Tracing",
        since = "1.0.0")
@Deprecated(since="4.0.0", forRemoval = true)
module io.helidon.microprofile.tracing {
    requires static io.helidon.common.features.api;

    requires jakarta.annotation;

    requires jakarta.ws.rs;
    requires jersey.common;
    requires io.opentracing.api;

    requires static jakarta.cdi;
    requires static jakarta.inject;

    requires io.helidon.microprofile.server;
    requires transitive io.helidon.microprofile.config;
    requires io.helidon.common;
    requires io.helidon.nima.webserver;
    requires io.helidon.jersey.common;
    requires io.helidon.nima.webserver.tracing;
    requires transitive io.helidon.tracing;
    requires transitive io.helidon.tracing.jersey;
    requires io.helidon.tracing.tracerresolver;
    requires io.helidon.tracing.opentelemetry;

    requires transitive microprofile.opentracing.api;
    requires microprofile.rest.client.api;
    requires io.opentracing.util;
    requires io.opentelemetry.opentracingshim;

    exports io.helidon.microprofile.tracing;

    // this is needed for CDI extensions that use non-public observer methods, and for constructor injection
    opens io.helidon.microprofile.tracing;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.tracing.TracingCdiExtension;
    provides org.glassfish.jersey.internal.spi.AutoDiscoverable
            with io.helidon.microprofile.tracing.MpTracingAutoDiscoverable;
    provides org.eclipse.microprofile.opentracing.ClientTracingRegistrarProvider
            with io.helidon.microprofile.tracing.MpTracingClientRegistrar;
    provides org.eclipse.microprofile.rest.client.spi.RestClientListener
            with io.helidon.microprofile.tracing.MpTracingRestClientListener;
}
