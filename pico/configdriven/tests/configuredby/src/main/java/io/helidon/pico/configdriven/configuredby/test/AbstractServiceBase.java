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

package io.helidon.pico.configdriven.configuredby.test;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public abstract class AbstractServiceBase implements SomeServiceInterface {
    private final AtomicInteger postConstructCallCount = new AtomicInteger();
    private final AtomicInteger preDestroyCallCount = new AtomicInteger();

    @PostConstruct
    void postConstruct() {
        postConstructCallCount.incrementAndGet();
    }

    int postConstructCallCount() {
        return postConstructCallCount.get();
    }

    @PreDestroy
    void preDestroy() {
        preDestroyCallCount.incrementAndGet();
    }

    int preDestroyCallCount() {
        return preDestroyCallCount.get();
    }

}
