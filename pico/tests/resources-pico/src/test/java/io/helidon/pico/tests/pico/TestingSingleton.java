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

package io.helidon.pico.tests.pico;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.pico.api.Resettable;
import io.helidon.pico.api.RunLevel;
import io.helidon.pico.tests.pico.stacking.Intercepted;
import io.helidon.pico.tests.pico.stacking.InterceptedImpl;
import io.helidon.pico.tests.pico.tbox.Awl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@RunLevel(RunLevel.STARTUP)
@Singleton
@Named("testing")
public class TestingSingleton extends InterceptedImpl implements Resettable {
    final static AtomicInteger postConstructCount = new AtomicInteger();
    final static AtomicInteger preDestroyCount = new AtomicInteger();

    @Inject Provider<Awl> awlProvider;

    @Inject
    TestingSingleton(Optional<Intercepted> inner) {
        super(inner);
    }

    @Override
    @PostConstruct
    public void voidMethodWithNoArgs() {
        postConstructCount.incrementAndGet();
    }

    @PreDestroy
    public void preDestroy() {
        preDestroyCount.incrementAndGet();
    }

    public static int postConstructCount() {
        return postConstructCount.get();
    }

    public static int preDestroyCount() {
        return preDestroyCount.get();
    }

    @Override
    public boolean reset(boolean deep) {
        postConstructCount.set(0);
        preDestroyCount.set(0);
        return true;
    }

}
