/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.configdriven.configuredby.test;

import java.util.Optional;

import io.helidon.builder.config.testsubjects.fakes.FakeServerConfig;
import io.helidon.builder.config.testsubjects.fakes.FakeTracer;
import io.helidon.pico.configdriven.api.ConfiguredBy;

import jakarta.inject.Inject;

@ConfiguredBy(value = FakeServerConfig.class, overrideBean = true, drivesActivation = false)
public class FakeWebServerNotDrivenAndHavingConfiguredByOverrides extends FakeWebServer {

    @Inject
    FakeWebServerNotDrivenAndHavingConfiguredByOverrides(FakeServerConfig cfg,
                                                         Optional<FakeTracer> tracer) {
        super(cfg, tracer);
    }

}
