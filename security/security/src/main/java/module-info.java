/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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
 * Java for cloud security module.
 *
 * @see io.helidon.security.Security
 * @see io.helidon.security.SecurityContext
 */
@Feature(value = "Security",
        description = "Security support",
        in = {HelidonFlavor.SE, HelidonFlavor.NIMA}
)
module io.helidon.security {
    requires java.logging;

    requires transitive io.helidon.common;
    requires transitive io.helidon.common.security;
    requires transitive io.helidon.common.configurable;
    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.config;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.tracing;

    requires io.helidon.security.util;
    requires io.helidon.common.context;
    requires io.helidon.common.uri;

    exports io.helidon.security;
    exports io.helidon.security.spi;

    exports io.helidon.security.internal to io.helidon.security.integration.jersey,
            io.helidon.security.integration.webserver,
            io.helidon.security.integration.nima,
            io.helidon.security.integration.grpc;

    // needed for CDI integration
    opens io.helidon.security to weld.core.impl, io.helidon.microprofile.cdi;

    uses io.helidon.security.spi.SecurityProviderService;
}
