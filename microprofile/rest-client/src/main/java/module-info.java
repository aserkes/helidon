/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
 * MP Rest client.
 *
 * @see org.eclipse.microprofile.rest.client
 */
@Feature(value = "REST Client",
        description = "MicroProfile REST client spec implementation",
        in = HelidonFlavor.MP,
        path = "REST Client"
)
@Aot(description = "Does not support execution of default methods on interfaces.")
module io.helidon.microprofile.restclient {
    requires static io.helidon.common.features.api;

    requires microprofile.rest.client.api;
    requires io.helidon.common.context;
    requires jersey.common;
    requires jersey.mp.rest.client;
    requires jakarta.ws.rs;

    exports io.helidon.microprofile.restclient;
    // needed for jersey injection
    opens io.helidon.microprofile.restclient to org.glassfish.hk2.utilities,weld.core.impl, io.helidon.microprofile.cdi;

    provides org.eclipse.microprofile.rest.client.spi.RestClientListener
            with io.helidon.microprofile.restclient.MpRestClientListener;
    provides org.glassfish.jersey.internal.spi.ForcedAutoDiscoverable
            with  io.helidon.microprofile.restclient.HelidonRequestHeaderAutoDiscoverable;
}
