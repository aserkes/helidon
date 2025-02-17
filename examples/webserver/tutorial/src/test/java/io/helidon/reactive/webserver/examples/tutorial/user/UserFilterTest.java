/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver.examples.tutorial.user;

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.testsupport.TestClient;
import io.helidon.reactive.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link UserFilter}.
 */
public class UserFilterTest {

    @Test
    public void filter() throws Exception {
        AtomicReference<User> userReference = new AtomicReference<>();
        Routing routing = Routing.builder()
                .any(new UserFilter())
                .any((req, res) -> {
                    userReference.set(req.context()
                                          .get(User.class)
                                          .orElse(null));
                    res.send();
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/")
                .get();
        assertThat(userReference.get(), is(User.ANONYMOUS));
        response = TestClient.create(routing)
                .path("/")
                .header(Http.Header.COOKIE, "Unauthenticated-User-Alias=Foo")
                .get();
        assertThat(userReference.get().getAlias(), is("Foo"));
    }
}
