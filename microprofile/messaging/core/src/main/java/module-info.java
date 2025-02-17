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
import io.helidon.common.features.api.Preview;

/**
 * MicroProfile Reactive Messaging implementation.
 *
 * @see org.eclipse.microprofile.reactive.messaging
 */
@Preview
@Feature(value = "Messaging",
        description = "MicroProfile Reactive Messaging spec implementation",
        in = HelidonFlavor.MP,
        path = "Messaging"
)
module io.helidon.microprofile.messaging {
    requires static io.helidon.common.features.api;

    requires java.logging;

    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires io.helidon.config;
    requires io.helidon.config.mp;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.reactive;
    requires transitive org.reactivestreams;
    requires transitive microprofile.reactive.messaging.api;
    requires transitive microprofile.reactive.streams.operators.api;
    requires io.helidon.common.reactive;

    exports io.helidon.microprofile.messaging;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.messaging to weld.core.impl, io.helidon.microprofile.cdi;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.messaging.MessagingCdiExtension;
}
