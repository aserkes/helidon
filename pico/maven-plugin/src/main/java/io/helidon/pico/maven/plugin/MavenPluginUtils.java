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

package io.helidon.pico.maven.plugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import io.helidon.builder.config.spi.ConfigBeanRegistryHolder;
import io.helidon.builder.config.spi.HelidonConfigBeanRegistry;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.api.BootstrapDefault;
import io.helidon.pico.api.Phase;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesHolder;
import io.helidon.pico.api.Resettable;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.tools.spi.ActivatorCreator;
import io.helidon.pico.tools.spi.ApplicationCreator;
import io.helidon.pico.tools.spi.ExternalModuleCreator;

import static io.helidon.pico.api.PicoServicesConfig.KEY_PERMITS_DYNAMIC;
import static io.helidon.pico.api.PicoServicesConfig.KEY_USES_COMPILE_TIME_APPLICATIONS;
import static io.helidon.pico.api.PicoServicesConfig.NAME;

final class MavenPluginUtils {
    private MavenPluginUtils() {
    }

    /**
     * Returns a {@link io.helidon.pico.api.Services} registry that forces application loading to be disabled.
     *
     * @return pico services
     */
    static PicoServices picoServices(boolean wantApps) {
        resetAll();
        return lazyCreate(basicConfig(wantApps)).get();
    }

    /**
     * Resets all internal Pico configuration instances, JVM global singletons, service registries, etc.
     */
    static void resetAll() {
        Internal.reset();
    }

    static ApplicationCreator applicationCreator() {
        return HelidonServiceLoader.create(ServiceLoader.load(ApplicationCreator.class)).iterator().next();
    }

    static ExternalModuleCreator externalModuleCreator() {
        return HelidonServiceLoader.create(ServiceLoader.load(ExternalModuleCreator.class)).iterator().next();
    }

    static ActivatorCreator activatorCreator() {
        return HelidonServiceLoader.create(ServiceLoader.load(ActivatorCreator.class)).iterator().next();
    }

    /**
     * Describe the provided instance or provider.
     *
     * @param providerOrInstance the instance to provider
     * @return the description of the instance
     */
    static String toDescription(Object providerOrInstance) {
        if (providerOrInstance instanceof Optional) {
            providerOrInstance = ((Optional<?>) providerOrInstance).orElse(null);
        }

        if (providerOrInstance instanceof ServiceProvider) {
            return ((ServiceProvider<?>) providerOrInstance).description();
        }
        return String.valueOf(providerOrInstance);
    }

    /**
     * Describe the provided instance or provider collection.
     *
     * @param coll the instance to provider collection
     * @return the description of the instance
     */
    static List<String> toDescriptions(Collection<?> coll) {
        return coll.stream().map(MavenPluginUtils::toDescription).collect(Collectors.toList());
    }

    static boolean hasValue(String val) {
        return (val != null && !val.isBlank());
    }

    static LazyValue<PicoServices> lazyCreate(Config config) {
        return LazyValue.create(() -> {
            PicoServices.globalBootstrap(BootstrapDefault.builder()
                                                 .config(config)
                                                 .limitRuntimePhase(Phase.GATHERING_DEPENDENCIES)
                                                 .build());
            return PicoServices.picoServices().orElseThrow();
        });
    }

    static Config basicConfig(boolean apps) {
        return Config.builder(ConfigSources.create(
                        Map.of(NAME + "." + KEY_PERMITS_DYNAMIC, "true",
                               NAME + "." + KEY_USES_COMPILE_TIME_APPLICATIONS, String.valueOf(apps)),
                        "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

    private static class Internal extends PicoServicesHolder {
        public static void reset() {
            PicoServicesHolder.reset();
            HelidonConfigBeanRegistry cbr = ConfigBeanRegistryHolder.configBeanRegistry().orElse(null);
            if (cbr instanceof Resettable) {
                ((Resettable) cbr).reset(true);
            }
        }
    }

}
