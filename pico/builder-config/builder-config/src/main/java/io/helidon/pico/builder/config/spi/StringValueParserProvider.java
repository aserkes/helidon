/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.config.spi;

/**
 * Java {@link java.util.ServiceLoader} provider interface for delivering the {@link StringValueParser} instance.
 *
 * @see io.helidon.pico.builder.config.spi.StringValueParserHolder
 */
public interface StringValueParserProvider {

    /**
     * The service-loaded global {@link StringValueParser} instance.
     *
     * @return the global string value parser instance
     */
    StringValueParser stringValueParser();

}
