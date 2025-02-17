/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.DirectClient;
import io.helidon.nima.testing.junit5.webserver.RoutingTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
class UnsentResponseTest {
    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/no-response", new NoResponseHandler())
                .get("/response", (req, res) -> {
                    res.send();
                });
    }

    @Test
    void testUnsentResponseThrowsException(DirectClient client) {
        try (Http1ClientResponse response = client.get("/no-response")
                .request()) {

            assertThat(response.status(), is(Http.Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.entity().as(String.class), is("Internal Server Error"));
        }
    }

    @Test
    void testNormalResponse(DirectClient client) {
        try (Http1ClientResponse response = client.get("/response")
                .request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
        }
    }

    // to have a bit nicer error output for the internal server error
    private static final class NoResponseHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
        }

        @Override
        public String toString() {
            return "NoResponseHandler";
        }
    }
}
