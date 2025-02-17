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

package io.helidon.common.types;

import org.junit.jupiter.api.Test;

import static io.helidon.common.types.TypeNameDefault.create;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TypedElementNameDefaultTest {

    @Test
    void declarations() {
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(boolean.class))
                           .build().toString(),
                   is("boolean arg"));
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(byte.class))
                           .build().toString(),
                   is("byte arg"));
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(short.class))
                           .build().toString(),
                   is("short arg"));
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(int.class))
                           .build().toString(),
                   is("int arg"));
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(long.class))
                           .build().toString(),
                   is("long arg"));
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(char.class))
                           .build().toString(),
                   is("char arg"));
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(float.class))
                           .build().toString(),
                   is("float arg"));
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(double.class))
                           .build().toString(),
                   is("double arg"));
        assertThat(TypedElementNameDefault.builder()
                           .elementName("arg")
                           .typeName(create(void.class))
                           .build().toString(),
                   is("void arg"));

        assertThat(TypedElementNameDefault.builder()
                           .enclosingTypeName(createFromTypeName("MyClass"))
                           .elementName("hello")
                           .typeName(create(void.class))
                           .elementKind(TypeInfo.KIND_METHOD)
                           .addParameterArgument(TypedElementNameDefault.builder()
                                                         .elementName("arg1")
                                                         .typeName(create(String.class))
                                                         .elementKind(TypeInfo.KIND_PARAMETER)
                                                         .build())
                           .addParameterArgument(TypedElementNameDefault.builder()
                                                         .elementName("arg2")
                                                         .typeName(create(int.class))
                                                         .elementKind(TypeInfo.KIND_PARAMETER)
                                                         .build())
                           .build().toString(),
                   is("MyClass::void hello(java.lang.String arg1, int arg2)"));
    }

}
