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

package io.helidon.pico.tests.plain.interceptor;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.TypeNameDefault;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.api.Interceptor;
import io.helidon.pico.api.InvocationContext;

@SuppressWarnings({"ALL", "unchecked"})
public class TestNamedInterceptor implements Interceptor {
    public static final AtomicInteger ctorCount = new AtomicInteger();

    public TestNamedInterceptor() {
        ctorCount.incrementAndGet();
    }

    @Override
    public <V> V proceed(InvocationContext ctx,
                         Chain<V> chain,
                         Object... args) {
        assert (ctx != null);

        TypedElementName methodInfo = ctx.elementInfo();
        if (methodInfo != null && methodInfo.typeName().equals(TypeNameDefault.create(long.class))) {
            V result = chain.proceed(args);
            long longResult = (Long) result;
            Object interceptedResult = (longResult * 2);
            return (V) interceptedResult;
        } else if (methodInfo != null && methodInfo.typeName().name().equals(String.class.getName())) {
            V result = chain.proceed(args);
            return (V) ("intercepted:" + result);
        } else {
            return chain.proceed(args);
        }
    }

}
