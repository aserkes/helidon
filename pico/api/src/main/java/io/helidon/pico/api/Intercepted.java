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

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * Indicates that type identified by {@link #value()} is being intercepted.
 *
 * @see io.helidon.pico.api.Interceptor
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Inherited
@Qualifier
@Target(java.lang.annotation.ElementType.TYPE)
public @interface Intercepted {

    /**
     * The target being intercepted.
     *
     * @return the target class being intercepted
     */
    Class<?> value();

}
