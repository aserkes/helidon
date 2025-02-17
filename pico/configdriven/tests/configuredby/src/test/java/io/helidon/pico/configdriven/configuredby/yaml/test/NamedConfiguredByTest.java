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

package io.helidon.pico.configdriven.configuredby.yaml.test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.config.spi.ConfigBeanRegistryHolder;
import io.helidon.builder.config.spi.GeneratedConfigBean;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.Services;
import io.helidon.pico.configdriven.runtime.ConfigBeanRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.testing.PicoTestingSupport.resetAll;
import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

class NamedConfiguredByTest {
    PicoServices picoServices;
    Services services;

    @BeforeAll
    static void initialStateChecks() {
        ConfigBeanRegistry cbr = (ConfigBeanRegistry) ConfigBeanRegistryHolder.configBeanRegistry().orElseThrow();
        assertThat(cbr.ready(), is(false));
    }

    @AfterAll
    static void tearDown() {
        resetAll();
    }

    void resetWith(Config config) {
        resetAll();
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    @BeforeEach
    void setup() {
        Optional<Bootstrap> existingBootstrap = PicoServices.globalBootstrap();
        assertThat(existingBootstrap, OptionalMatcher.optionalEmpty());

        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml"))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        resetWith(config);
    }

    @Disabled("Will be addressed in #6674")
    @Test
    void namedConfiguredServices() {
        ConfigBeanRegistry cbr = (ConfigBeanRegistry) ConfigBeanRegistryHolder.configBeanRegistry().orElseThrow();
        Map<String, Collection<GeneratedConfigBean>> all = cbr.allConfigBeans();
        assertThat(all.keySet(),
                   containsInAnyOrder("ft.asyncs.first", "ft.asyncs.second", "ft.bulkheads", "server"));
    }

}
