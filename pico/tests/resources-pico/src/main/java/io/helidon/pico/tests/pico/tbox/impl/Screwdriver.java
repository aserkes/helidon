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

package io.helidon.pico.tests.pico.tbox.impl;

import java.io.Serializable;
import java.util.Optional;

import io.helidon.pico.tests.pico.SomeOtherLocalNonContractInterface1;
import io.helidon.pico.tests.pico.tbox.Tool;

import jakarta.inject.Singleton;

@Singleton
public class Screwdriver implements Tool, SomeOtherLocalNonContractInterface1, Serializable {

    @Override
    public Optional<String> named() {
        return Optional.of("screwdriver");
    }

}
