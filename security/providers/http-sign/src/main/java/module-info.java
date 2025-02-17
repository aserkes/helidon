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
 * Http signature provider.
 */
@Feature(value = "HTTP Signatures",
        description = "Security provider for HTTP Signature authentication and outbound",
        in = {HelidonFlavor.SE, HelidonFlavor.MP, HelidonFlavor.NIMA},
        path = {"Security", "Provider", "HttpSign"}
)
module io.helidon.security.providers.httpsign {
    requires static io.helidon.common.features.api;

    requires static io.helidon.config.metadata;

    requires transitive io.helidon.config;
    requires transitive io.helidon.common;
    requires transitive io.helidon.security;
    requires transitive io.helidon.common.pki;
    requires transitive io.helidon.security.util;
    requires transitive io.helidon.security.providers.common;

    exports io.helidon.security.providers.httpsign;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.httpsign.HttpSignService;
}
