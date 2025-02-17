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

package io.helidon.pico.runtime.testsubjects;

import java.util.Optional;

import io.helidon.pico.api.Application;
import io.helidon.pico.api.ServiceInjectionPlanBinder;

import jakarta.annotation.Generated;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * For testing.
 */
@Generated(value = "example", comments = "API Version: n")
@Singleton
@Named(HelloPico$$Application.NAME)
public class HelloPico$$Application implements Application {
    public static boolean ENABLED = true;

    static final String NAME = "HelloPicoApplication";

    public HelloPico$$Application() {
        assert(true); // for setting breakpoints in debug
    }

    @Override
    public Optional<String> named() {
        return Optional.of(NAME);
    }

    @Override
    public void configure(ServiceInjectionPlanBinder binder) {
        if (!ENABLED) {
            return;
        }

        binder.bindTo(HelloPicoImpl$$picoActivator.INSTANCE)
                .bind(HelloPicoWorld.class.getPackageName() + ".world", PicoWorldImpl$$picoActivator.INSTANCE)
                .bind(HelloPicoWorld.class.getPackageName() + ".worldRef", PicoWorldImpl$$picoActivator.INSTANCE)
                .bindMany(HelloPicoWorld.class.getPackageName() + ".listOfWorldRefs", PicoWorldImpl$$picoActivator.INSTANCE)
                .bindMany(HelloPicoWorld.class.getPackageName() + ".listOfWorlds", PicoWorldImpl$$picoActivator.INSTANCE)
                .bindVoid(HelloPicoWorld.class.getPackageName() + ".redWorld")
                .bind(HelloPicoWorld.class.getPackageName() + ".world|1(1)", PicoWorldImpl$$picoActivator.INSTANCE)
                .commit();

        binder.bindTo(PicoWorldImpl$$picoActivator.INSTANCE)
                .commit();
    }

}
