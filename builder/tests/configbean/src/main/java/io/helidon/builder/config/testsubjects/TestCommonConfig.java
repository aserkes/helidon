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

package io.helidon.builder.config.testsubjects;

import java.util.List;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.builder.config.ConfigBean;

/**
 * For testing purpose.
 */
@ConfigBean
@Builder(allowNulls = true)
public interface TestCommonConfig {

    /**
     * For testing purpose.
     *
     * @return for testing purposes
     */
    String name();

    /**
     * For testing purpose.
     *
     * @return for testing purposes
     */
    @ConfiguredOption(required = true)
    int port();

    /**
     * For testing purpose.
     *
     * @return for testing purposes
     */
    List<String> cipherSuites();

    /**
     * For testing purpose.
     *
     * @return for testing purposes
     */
    char[] pswd();

}
