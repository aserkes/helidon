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

package io.helidon.builder.config.testsubjects.fakes;

import java.util.Map;

import io.helidon.builder.Singular;
import io.helidon.builder.config.ConfigBean;

/**
 * aka TracingConfig.
 *
 * Tracing configuration that contains traced components (such as WebServer, Security) and their traced spans and span logs.
 * Spans can be renamed through configuration, components, spans and span logs may be disabled through this configuration.
 */
@ConfigBean("tracing")
public interface FakeTracingConfig extends FakeTraceableConfig {

    @Singular("component")  // Builder::addComponent(String component); Impl::getComponent(String component);
    Map<String, FakeComponentTracingConfig> components();

}
