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

package io.helidon.pico.tools;

import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.ServiceInfoBasics;

/**
 * The specifics for a single {@link io.helidon.pico.api.ServiceProvider} that was code generated.
 *
 * @see ActivatorCreatorResponse#serviceTypeDetails()
 */
@Builder
public interface ActivatorCodeGenDetail extends GeneralCodeGenDetail {

    /**
     * The additional meta-information describing what is offered by the generated service.
     *
     * @return additional meta-information describing the generated service info
     */
    ServiceInfoBasics serviceInfo();

    /**
     * The additional meta-information describing what the generated service depends upon.
     *
     * @return additional meta-information describing what the generated service depends upon
     */
    Optional<DependenciesInfo> dependencies();

}
