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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Microprofile messaging Kafka connector.
 */
@Preview
@Feature(value = "Kafka Connector",
        description = "Reactive messaging connector for Kafka",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Messaging", "Kafka"}
)
module io.helidon.messaging.connectors.kafka {
    requires static io.helidon.common.features.api;

    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires static kafka.clients;
    requires org.reactivestreams;
    requires transitive io.helidon.config;
    requires io.helidon.config.mp;
    requires transitive microprofile.reactive.messaging.api;
    requires transitive microprofile.reactive.streams.operators.api;
    requires io.helidon.common.context;
    requires io.helidon.common.reactive;
    requires io.helidon.common.configurable;
    requires io.helidon.messaging;
    requires microprofile.config.api;
    requires static org.graalvm.sdk;
    requires java.security.sasl;
    requires transitive org.slf4j;
    // To allow KerberosLoginSubstitution
    requires java.security.jgss;

    exports io.helidon.messaging.connectors.kafka;
}
