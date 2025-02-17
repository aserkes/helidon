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

import io.helidon.builder.Builder;

/**
 * The response from {@link io.helidon.pico.tools.spi.ExternalModuleCreator}.
 * <p>
 * The response, if successful, will contribute to the {@link ActivatorCreatorRequest}
 * passed to {@link io.helidon.pico.tools.spi.ActivatorCreator} in any next phase of creation for the external Pico module.
 */
@Builder
public interface ExternalModuleCreatorResponse extends GeneralCreatorResponse {

    /**
     * The activator creator request.
     *
     * @return the activator creator request
     */
    ActivatorCreatorRequest activatorCreatorRequest();

}
