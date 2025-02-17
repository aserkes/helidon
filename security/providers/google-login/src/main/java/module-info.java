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
 * Google login authentication provider.
 */
@Feature(value = "Google Login",
        description = "Security provider for Google login button authentication and outbound",
        in = {HelidonFlavor.SE, HelidonFlavor.MP, HelidonFlavor.NIMA},
        path = {"Security", "Provider", "Google-Login"}
)
@Aot(false)
module io.helidon.security.providers.google.login {
    requires static io.helidon.common.features.api;

    requires io.helidon.config;
    requires io.helidon.common;
    requires io.helidon.security;
    requires google.api.client;
    requires com.google.api.client;
    requires com.google.api.client.json.gson;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.util;
    requires io.helidon.tracing;

    requires static io.helidon.config.metadata;

    exports io.helidon.security.providers.google.login;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.google.login.GoogleTokenService;
}
