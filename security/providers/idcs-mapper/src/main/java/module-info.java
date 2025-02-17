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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * IDCS role mapper.
 */
@Feature(value = "IDCS Role Mapper",
        description = "Security provider role mapping - Oracle IDCS",
        in = {HelidonFlavor.SE, HelidonFlavor.MP, HelidonFlavor.NIMA},
        path = {"Security", "Provider", "IdcsRoleMapper"}
)
@Aot(false)
module io.helidon.security.providers.idcs.mapper {
    requires static io.helidon.common.features.api;

    requires transitive io.helidon.config;
    requires transitive io.helidon.common;
    requires transitive io.helidon.security;
    requires transitive io.helidon.security.providers.common;
    requires transitive io.helidon.security.jwt;
    requires transitive io.helidon.security.providers.oidc.common;

    requires static io.helidon.config.metadata;

    requires io.helidon.security.integration.common;
    requires io.helidon.security.util;
    requires io.helidon.reactive.webclient;

    requires jersey.client;
    requires jakarta.ws.rs;

    exports io.helidon.security.providers.idcs.mapper;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProviderService;
}
