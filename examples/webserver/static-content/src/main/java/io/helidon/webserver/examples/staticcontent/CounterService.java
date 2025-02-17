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

package io.helidon.webserver.examples.staticcontent;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

/**
 * Counts access to the WEB service.
 */
public class CounterService implements HttpService {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private final LongAdder allAccessCounter = new LongAdder();
    private final AtomicInteger apiAccessCounter = new AtomicInteger();

    @Override
    public void routing(HttpRules rules) {
        rules.any(this::handleAny)
                .get("/api/counter", this::handleGet);
    }

    private void handleAny(ServerRequest request, ServerResponse response) {
        allAccessCounter.increment();
        response.next();
    }

    private void handleGet(ServerRequest request, ServerResponse response) {
        int apiAcc = apiAccessCounter.incrementAndGet();
        JsonObject result = JSON.createObjectBuilder()
                                .add("all", allAccessCounter.longValue())
                                .add("api", apiAcc)
                                .build();
        response.send(result);
    }
}
