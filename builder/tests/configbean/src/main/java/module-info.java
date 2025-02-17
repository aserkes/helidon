/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * Helidon ConfigBean Builder test Module (i.e., only common config and w/o config-driven services).
 */
module io.helidon.builder.config.tests.test.config {
    requires static jakarta.inject;
    requires static jakarta.annotation;
    requires static io.helidon.config.metadata;
    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.builder;
    requires io.helidon.builder.config;

    exports io.helidon.builder.config.testsubjects;
}
