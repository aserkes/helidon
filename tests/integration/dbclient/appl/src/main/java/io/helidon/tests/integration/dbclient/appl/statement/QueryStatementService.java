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
package io.helidon.tests.integration.dbclient.appl.statement;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.helidon.common.reactive.Multi;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbRow;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.RangePoJo;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to test DbStatementQuery methods.
 */
public class QueryStatementService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(GetStatementService.class.getName());

    private interface TestFunction extends BiFunction<Integer, Integer, Multi<DbRow>> {}

    public QueryStatementService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testQueryArrayParams", this::testQueryArrayParams)
                .get("/testQueryListParams", this::testQueryListParams)
                .get("/testQueryMapParams", this::testQueryMapParams)
                .get("/testQueryOrderParam", this::testQueryOrderParam)
                .get("/testQueryNamedParam", this::testQueryNamedParam)
                .get("/testQueryMappedNamedParam", this::testQueryMappedNamedParam)
                .get("/testQueryMappedOrderParam", this::testQueryMappedOrderParam);
    }

    // Common test execution code
    private void executeTest(
            ServerRequest request,
            ServerResponse response,
            String testName,
            TestFunction test
    ) {
        try {
            String fromIdStr = param(request, QUERY_FROM_ID_PARAM);
            int fromId = Integer.parseInt(fromIdStr);
            String toIdStr = param(request, QUERY_TO_ID_PARAM);
            int toId = Integer.parseInt(toIdStr);
            Multi<DbRow> future = test.apply(fromId, toId);
            final JsonArrayBuilder jab = Json.createArrayBuilder();
            future.forEach(dbRow -> jab.add(dbRow.as(JsonObject.class)))
                    .onComplete(() -> response.send(okStatus(jab.build())))
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (NumberFormatException | RemoteTestException ex) {
            LOGGER.log(Level.WARNING, String.format("Error in SimpleQueryService.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code params(Object... parameters)} parameters setting method.
    private void testQueryArrayParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryArrayParams",
                    (fromId, toId) -> dbClient().execute(
                            exec -> exec
                                    .createNamedQuery("select-pokemons-idrng-order-arg")
                                    .params(fromId, toId)
                                    .execute()
                    ));
    }

    // Verify {@code params(List<?>)} parameters setting method.
    private void testQueryListParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryListParams",
                    (fromId, toId) -> dbClient().execute(
                            exec -> {
                                List<Integer> params = new ArrayList<>(2);
                                params.add(fromId);
                                params.add(toId);
                                return exec
                                        .createNamedQuery("select-pokemons-idrng-order-arg")
                                        .params(params)
                                        .execute();
                            }
                    ));
    }

    // Verify {@code params(Map<?>)} parameters setting method.
    private void testQueryMapParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryMapParams",
                    (fromId, toId) -> dbClient().execute(
                            exec -> {
                                Map<String, Integer> params = new HashMap<>(2);
                                params.put("idmin", fromId);
                                params.put("idmax", toId);
                                return exec
                                        .createNamedQuery("select-pokemons-idrng-named-arg")
                                        .params(params)
                                        .execute();
                            }
                    ));
    }

    // Verify {@code addParam(Object parameter)} parameters setting method.
    private void testQueryOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryOrderParam",
                    (fromId, toId) -> dbClient().execute(
                            exec -> exec
                                    .createNamedQuery("select-pokemons-idrng-order-arg")
                                    .addParam(fromId)
                                    .addParam(toId)
                                    .execute()
                    ));
    }

    // Verify {@code addParam(String name, Object parameter)} parameters setting method.
    private void testQueryNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryNamedParam",
                    (fromId, toId) -> dbClient().execute(
                            exec -> exec
                                    .createNamedQuery("select-pokemons-idrng-named-arg")
                                    .addParam("idmin", fromId)
                                    .addParam("idmax", toId)
                                    .execute()
                    ));
    }

    // Verify {@code namedParam(Object parameters)} mapped parameters setting method.
    private void testQueryMappedNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryMappedNamedParam",
                    (fromId, toId) -> dbClient().execute(
                            exec -> {
                                RangePoJo range = new RangePoJo(fromId, toId);
                                return exec
                                        .createNamedQuery("select-pokemons-idrng-named-arg")
                                        .namedParam(range)
                                        .execute();
                            }
                    ));
    }

    // Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
    private void testQueryMappedOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryMappedOrderParam",
                    (fromId, toId) -> dbClient().execute(
                            exec -> {
                                RangePoJo range = new RangePoJo(fromId, toId);
                                return exec
                                        .createNamedQuery("select-pokemons-idrng-order-arg")
                                        .indexedParam(range)
                                        .execute();
                            }
                    ));
    }

}
