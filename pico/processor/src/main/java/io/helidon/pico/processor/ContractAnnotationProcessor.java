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

package io.helidon.pico.processor;

import java.util.Set;

import javax.lang.model.element.TypeElement;

import io.helidon.pico.tools.TypeNames;

/**
 * Handles {@code @Contract} annotations.
 */
public class ContractAnnotationProcessor extends BaseAnnotationProcessor<Void> {

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public ContractAnnotationProcessor() {
    }

    @Override
    public Set<String> annoTypes() {
        return Set.of(TypeNames.PICO_CONTRACT);
    }

    @Override
    public void doInner(TypeElement type,
                        Void ignored) {
        // NOP (disable base processing)
    }

}
