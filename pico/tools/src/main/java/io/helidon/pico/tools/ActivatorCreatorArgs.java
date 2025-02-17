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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.common.types.TypeName;
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.ServiceInfoBasics;

/**
 * See {@link ActivatorCreatorDefault}.
 */
@Builder
abstract class ActivatorCreatorArgs {
    abstract String template();
    abstract TypeName serviceTypeName();
    abstract TypeName activatorTypeName();
    abstract Optional<String> activatorGenericDecl();
    abstract Optional<TypeName> parentTypeName();
    abstract Set<String> scopeTypeNames();
    abstract List<String> description();
    abstract ServiceInfoBasics serviceInfo();
    abstract Optional<DependenciesInfo> dependencies();
    abstract Optional<DependenciesInfo> parentDependencies();
    abstract Collection<Object> injectionPointsSkippedInParent();
    abstract List<?> serviceTypeInjectionOrder();
    abstract String generatedSticker();
    abstract Optional<Double> weightedPriority();
    abstract Optional<Integer> runLevel();
    abstract Optional<String> postConstructMethodName();
    abstract Optional<String> preDestroyMethodName();
    abstract List<String> extraCodeGen();
    abstract List<String> extraClassComments();
    abstract boolean isConcrete();
    abstract boolean isProvider();
    abstract boolean isSupportsJsr330InStrictMode();
}
