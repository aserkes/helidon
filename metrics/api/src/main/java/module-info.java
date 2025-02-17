/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import io.helidon.metrics.api.spi.ExemplarService;
import io.helidon.metrics.api.spi.RegistryFactoryProvider;

/**
 * Helidon metrics API.
 */
module io.helidon.metrics.api {

    requires io.helidon.common.http;
    requires transitive io.helidon.config;

    requires transitive microprofile.metrics.api;
    requires static io.helidon.config.metadata;

    exports io.helidon.metrics.api;
    exports io.helidon.metrics.api.spi;

    uses RegistryFactoryProvider;
    uses ExemplarService;
}
