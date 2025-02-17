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

package io.helidon.pico.api;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;

/**
 * Aggregates the set of {@link InjectionPointInfo}'s that are dependent upon a specific and common
 * {@link ServiceInfo} definition.
 */
@Builder
public interface DependencyInfo {

    /**
     * The service info describing what the injection point dependencies are dependent upon.
     *
     * @return the service info dependency
     */
    ServiceInfoCriteria dependencyTo();

    /**
     * The set of injection points that depends upon {@link #dependencyTo()}.
     *
     * @return the set of dependencies
     */
    @Singular("injectionPointDependency")
    Set<? extends InjectionPointInfo> injectionPointDependencies();

    /**
     * The {@link io.helidon.pico.api.ServiceProvider} that this dependency is optional resolved and bound to. All dependencies
     * from {@link #injectionPointDependencies()} will be bound to this resolution.
     *
     * @return the optional resolved and bounded service provider
     */
    Optional<ServiceProvider<?>> resolvedTo();

}
