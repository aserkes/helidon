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

package io.helidon.reactive.webserver.examples.tutorial;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.reactive.webserver.WebServer;
import io.helidon.reactive.webserver.testsupport.TestClient;
import io.helidon.reactive.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link Main}.
 */
public class MainTest {

    @Test
    public void testShutDown() throws Exception {
        TestResponse response = TestClient.create(Main.createRouting())
                .path("/mgmt/shutdown")
                .post();
        assertThat(response.status(), is(Http.Status.OK_200));
        CountDownLatch latch = new CountDownLatch(1);
        WebServer webServer = response.webServer();
                webServer
                        .whenShutdown()
                        .thenRun(latch::countDown);
        assertThat(latch.await(10, TimeUnit.SECONDS), is(true));
    }
}
