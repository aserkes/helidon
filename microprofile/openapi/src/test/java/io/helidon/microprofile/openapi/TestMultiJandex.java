/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.io.IOException;

import io.helidon.microprofile.openapi.other.TestApp2;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestMultiJandex {

    @Test
    public void testMultipleIndexFiles() throws IOException {

        /*
         * The pom builds two differently-named test Jandex files, as an approximation
         * to handling multiple same-named index files in the class path.
         */
        OpenApiCdiExtension builder = new OpenApiCdiExtension("META-INF/jandex.idx", "META-INF/other.idx");
        IndexView indexView = builder.indexView();

        DotName testAppName = DotName.createSimple(TestApp.class.getName());
        DotName testApp2Name = DotName.createSimple(TestApp2.class.getName());

        ClassInfo testAppInfo = indexView.getClassByName(testAppName);
        assertThat("Expected index entry for TestApp not found", testAppInfo, notNullValue());

        ClassInfo testApp2Info = indexView.getClassByName(testApp2Name);
        assertThat("Expected index entry for TestApp2 not found", testApp2Info, notNullValue());
    }
}