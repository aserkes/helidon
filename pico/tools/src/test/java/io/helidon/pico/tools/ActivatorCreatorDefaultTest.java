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

import java.util.Collections;
import java.util.List;

import io.helidon.common.types.TypeNameDefault;
import io.helidon.pico.tools.spi.ActivatorCreator;
import io.helidon.pico.tools.testsubjects.HelloPicoWorldImpl;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ActivatorCreatorDefault}.
 */
class ActivatorCreatorDefaultTest extends AbstractBaseCreator {

    final ActivatorCreator activatorCreator = loadAndCreate(ActivatorCreator.class);

    @Test
    void sanity() {
        assertThat(activatorCreator.getClass(), equalTo(ActivatorCreatorDefault.class));
    }

    /**
     * Note that most of the "real" functional testing will need to occur downstream from this module and test.
     */
    @Test
    void codegenHelloActivator() {
        ActivatorCreatorDefault activatorCreator = (ActivatorCreatorDefault) this.activatorCreator;
        CodeGenPaths codeGenPaths = CodeGenPathsDefault.builder()
                .generatedSourcesPath("target/pico/generated-sources")
                .outputPath("target/pico/generated-classes")
                .build();
        AbstractFilerMessager directFiler = AbstractFilerMessager
                .createDirectFiler(codeGenPaths, System.getLogger(getClass().getName()));
        CodeGenFiler filer = CodeGenFiler.create(directFiler);
        ActivatorCreatorCodeGenDefault codeGen = ActivatorCreatorCodeGenDefault.builder().build();
        ActivatorCreatorRequest req = ActivatorCreatorRequestDefault.builder()
                .serviceTypeNames(List.of(TypeNameDefault.create(HelloPicoWorldImpl.class)))
                .codeGen(codeGen)
                .codeGenPaths(codeGenPaths)
                .configOptions(ActivatorCreatorConfigOptionsDefault.builder().build())
                .filer(filer)
                .build();

        ToolsException te = assertThrows(ToolsException.class, () -> activatorCreator.createModuleActivators(req));
        assertEquals("Failed in create", te.getMessage());

        ActivatorCreatorRequest req2 = ActivatorCreatorRequestDefault.builder()
                .serviceTypeNames(Collections.singletonList(TypeNameDefault.create(HelloPicoWorldImpl.class)))
                .codeGenPaths(CodeGenPathsDefault.builder().build())
                .throwIfError(Boolean.FALSE)
                .codeGen(codeGen)
                .configOptions(ActivatorCreatorConfigOptionsDefault.builder().build())
                .filer(filer)
                .build();
        ActivatorCreatorResponse res = activatorCreator.createModuleActivators(req2);
        assertThat(res.toString(), res.success(), is(false));
        assertThat(res.error().orElseThrow().getMessage(), equalTo("Failed in create"));
    }

}
