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
import io.helidon.common.LazyValue;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Request to deactivate a {@link io.helidon.pico.api.ServiceProvider}.
 */
@Builder
public abstract class DeActivationRequest {

    DeActivationRequest() {
    }

    /**
     * Whether to throw an exception on failure, or return it as part of the result.
     *
     * @return throw on failure
     */
    @ConfiguredOption("true")
    public abstract boolean throwIfError();

    /**
     * A standard/default deactivation request, without any additional options placed on the request.
     *
     * @return a standard/default deactivation request.
     */
    public static DeActivationRequest defaultDeactivationRequest() {
        return Init.DEFAULT.get();
    }


    static class Init {
        static final LazyValue<DeActivationRequest> DEFAULT =
                LazyValue.create(() -> DeActivationRequestDefault.builder().build());
    }

}
