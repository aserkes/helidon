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

import io.helidon.builder.Builder;

/**
 * Describes a receiver for injection - identifies who/what is requesting an injection that needs to be satisfied.
 */
@Builder
public interface InjectionPointInfo extends ElementInfo {

    /**
     * The identity (aka id) for this injection point. The id should be unique for the service type it is contained within.
     * <p>
     * This method will return the {@link #baseIdentity()} when {@link #elementOffset()} is null.  If not null
     * then the elemOffset is part of the returned identity.
     *
     * @return the unique identity
     */
    String id();

    /**
     * The base identifying name for this injection point. If the element represents a function, then the function arguments
     * are encoded in its base identity.
     *
     * @return the base identity of the element
     */
    String baseIdentity();

    /**
     * True if the injection point is of type {@link java.util.List}.
     *
     * @return true if list based receiver
     */
    boolean listWrapped();

    /**
     * True if the injection point is of type {@link java.util.Optional}.
     *
     * @return true if optional based receiver
     */
    boolean optionalWrapped();

    /**
     * True if the injection point is of type Provider (or Supplier).
     *
     * @return true if provider based receiver
     */
    boolean providerWrapped();

    /**
     * The service info criteria/dependency this is dependent upon.
     *
     * @return the service info criteria we are dependent upon
     */
    ServiceInfoCriteria dependencyToServiceInfo();

}
