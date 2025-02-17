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

/**
 * Helidon LRA coordinator.
 */
module io.helidon.lra.coordinator {
    requires jakarta.json;
    requires microprofile.lra.api;
    requires io.helidon.common.reactive;
    requires io.helidon.reactive.webclient;
    requires io.helidon.nima.webserver;
    requires io.helidon.nima.observe.metrics;
    requires io.helidon.nima.observe.health;
    requires io.helidon.scheduling;
    requires io.helidon.reactive.dbclient;
    requires io.helidon.reactive.dbclient.jdbc;
    requires io.helidon.health.checks;
    requires io.helidon.logging.common;
    requires io.helidon.metrics.api;
}