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
 * The Pico API provide these annotation types that are typically used at compile time
 * to assign special meaning to the type. It is used in conjunction with Helidon tooling (see the {@code pico-processor} and
 * {@code pico-maven-plugin} modules) to create and validate the DI module at compile time.
 * <ul>
 * <li>{@link io.helidon.pico.api.Contract} - signifies that the type can be used for lookup in the service registry.</li>
 * <li>{@link io.helidon.pico.api.ExternalContracts} - same as Contract, but applied to the implementation class instead.</li>
 * <li>{@link io.helidon.pico.api.RunLevel} - ascribes meaning for when the service should start.</li>
 * </ul>
 * Also note that the set of annotations from both the {@code jakarta.inject} and {@code jakarta.annotation} modules are the
 * primary way to annotate your DI model types.
 * <p>
 * Other types from the API are less commonly used, but are still made available for situations where programmatic access
 * is required or desirable in some way. The two most common types for entry into this part of the API are shown below.
 * <ul>
 * <li>{@link io.helidon.pico.api.PicoServices} - suite of services that are typically delivered by the Pico provider.</li>
 * <li>{@link io.helidon.pico.api.Services} - the services registry, which is one such service from this suite.</li>
 * </ul>
 */
package io.helidon.pico.api;
