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

package io.helidon.pico.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Weight;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.Resettable;
import io.helidon.pico.spi.PicoServicesProvider;

import jakarta.inject.Singleton;

import static io.helidon.pico.api.ServiceInfoBasics.DEFAULT_PICO_WEIGHT;

/**
 * The default implementation for {@link io.helidon.pico.spi.PicoServicesProvider}.
 * The first instance created (or first after calling deep {@link #reset}) will be the global services instance. The global
 * instance will track the set of loaded modules and applications that are loaded by this JVM.
 *
 * @see io.helidon.pico.api.PicoServices#picoServices()
 */
@Singleton
@Weight(DEFAULT_PICO_WEIGHT)
public class DefaultPicoServicesProvider implements PicoServicesProvider, Resettable {
    private static final AtomicReference<DefaultPicoServices> INSTANCE = new AtomicReference<>();

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public DefaultPicoServicesProvider() {
    }

    @Override
    public PicoServices services(Bootstrap bootstrap) {
        Objects.requireNonNull(bootstrap);
        if (INSTANCE.get() == null) {
            DefaultPicoServices global = new DefaultPicoServices(bootstrap, true);
            INSTANCE.compareAndSet(null, global);
        }

        if (INSTANCE.get().bootstrap().equals(bootstrap)) {
            // the global one
            return INSTANCE.get();
        }

        // not the global one
        return new DefaultPicoServices(bootstrap, false);
    }

    @Override
    public boolean reset(boolean deep) {
        DefaultPicoServices services = INSTANCE.get();
        boolean result = (services != null);
        if (services != null) {
            services.reset(deep);
            if (deep) {
                INSTANCE.set(null);
            }
        }
        return result;
    }

}
