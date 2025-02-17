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

package io.helidon.pico.tests.tck.jsr330;

import java.util.Enumeration;
import java.util.Objects;

import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesConfig;

import jakarta.inject.Provider;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import org.atinject.tck.Tck;
import org.atinject.tck.auto.Car;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Jsr-330 TCK Testing.
 * This test requires the annotation processing and the maven-plugin to run - see pom.xml.
 */
class Jsr330TckTest {

    /**
     * Run's the TCK tests.
     */
    @Test
    void testItAll() {
        PicoServices picoServices = PicoServices.picoServices().orElseThrow();
        PicoServicesConfig cfg = picoServices.config();
        Provider<Car> carProvider = picoServices.services().lookupFirst(Car.class);
        Objects.requireNonNull(carProvider.get());
        assertThat("sanity", carProvider.get(), not(carProvider.get()));
        junit.framework.Test jsrTest = Tck.testsFor(carProvider.get(),
                                                    cfg.supportsJsr330Statics(),
                                                    cfg.supportsJsr330Privates());
        TestResult result = new TestResult();
        jsrTest.run(result);
        assertThat(result.runCount(), greaterThan(0));
        assertThat(toFailureReport(result), result.wasSuccessful(), is(true));
    }

    String toFailureReport(TestResult result) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        Enumeration<TestFailure> failures = result.failures();
        while (failures.hasMoreElements()) {
            TestFailure failure = failures.nextElement();
            builder.append("\nFAILURE #").append(++count).append(" : ")
                    .append(failure.trace())
                    .append("\n");
        }
        return builder.toString();
    }

}
