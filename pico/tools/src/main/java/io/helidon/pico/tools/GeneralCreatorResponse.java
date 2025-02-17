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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.common.types.TypeName;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * General base interface for any codegen-related create activity.
 */
@Builder
public interface GeneralCreatorResponse extends GeneralCodeGenNames {

    /**
     * Flag to indicate a success or failure.
     *
     * @return success flag
     */
    @ConfiguredOption("true")
    boolean success();

    /**
     * Any error that was caught during processing.
     *
     * @return any error that was thrown
     */
    Optional<Throwable> error();

    // java source related ...

    /**
     * The services that were generated.
     *
     * @return the services that were generated
     */
    List<TypeName> serviceTypeNames();

    /**
     * The detailed information generated for those service type involved in code generation.
     *
     * @return map of service type names to generated details
     */
    @Singular
    Map<TypeName, ? extends GeneralCodeGenDetail> serviceTypeDetails();

    // resources related ...

    /**
     * The META-INF services entries.
     *
     * @return the META-INF services entries
     */
    Map<String, List<String>> metaInfServices();

}
