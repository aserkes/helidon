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
package io.helidon.tests.integration.dbclient.appl.result;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import io.helidon.common.reactive.Multi;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbRow;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Type;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test proper flow control handling in query processing.
 */
public class FlowControlService extends AbstractService {

    /** Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(FlowControlService.class.getName());

    /**
     * Creates an instance of web resource to test proper flow control handling in query processing.
     *
     * @param dbClient DbClient instance
     * @param statements statements from configuration file
     */
    public FlowControlService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testSourceData", this::testSourceData)
                .get("/testFlowControl", this::testFlowControl);
    }

    /**
     * Test subscriber.
     * Verifies proper flow control handling of returned pokemon types.
     */
    private static final class TestSubscriber implements Subscriber<DbRow> {

        /** Requests sequence. Total amount of pokemon types is 18. */
        private static final int[] REQUESTS = new int[] {3, 5, 4, 6, 1};

        /** Subscription instance. */
        private Subscription subscription;
        /** Current index of REQUESTS array. */
        private int reqIdx;
        /** Currently requested amount. */
        private int requested;
        /** Currently remaining from last request. */
        private int remaining;
        /** Total amount of records processed. */
        private int total;
        /** Whether processing was finished. */
        private boolean finished;
        /** Error message to terminate test. */
        private String error;

        private TestSubscriber() {
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            total = 0;
            reqIdx = 0;
            finished = false;
            error = null;
            // Initially request 3 DbRows.
            requested = REQUESTS[reqIdx];
            remaining = REQUESTS[reqIdx++];
            LOGGER.log(Level.DEBUG, () -> String.format("Requesting first rows: %d", requested));
            this.subscription.request(requested);
        }

        @Override
        public void onNext(DbRow dbRow) {
            final Type type = new Type(dbRow.column(1).as(Integer.class), dbRow.column(2).as(String.class));
            total++;
            if (remaining > 0) {
                remaining -= 1;
                LOGGER.log(Level.DEBUG, () -> String.format(
                        "NEXT: tot: %d req: %d rem: %d type: %s", total, requested, remaining, type.toString()));
                if (remaining == 0 && reqIdx < REQUESTS.length) {
                    LOGGER.log(Level.DEBUG, () -> "Notifying main thread to request more rows");
                    synchronized (this) {
                        this.notify();
                    }
                }
                // Shall not recieve dbRow when not requested!
            } else {
                LOGGER.log(Level.WARNING, () -> String.format(
                        "NEXT: tot: %d req: %d rem: %d type: %s", total, requested, remaining, type.toString()));
                throw new RemoteTestException(String.format("Recieved unexpected row: %s", type.toString()));
            }
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable.getMessage();
            LOGGER.log(Level.WARNING, String.format("EXCEPTION: %s", throwable.getMessage()), throwable);
            finished = true;
        }

        @Override
        public void onComplete() {
            LOGGER.log(Level.DEBUG, () -> String.format("COMPLETE: tot: %d req: %d rem: %d", total, requested, remaining));
            finished = true;
            synchronized (this) {
                this.notify();
            }
        }

        private boolean canRequestNext() {
            return remaining == 0 && reqIdx < REQUESTS.length;
        }

        private void requestNext() {
            if (reqIdx < REQUESTS.length) {
                requested = remaining = REQUESTS[reqIdx++];
                LOGGER.log(Level.DEBUG, () -> String.format("Requesting more rows: %d", requested));
                this.subscription.request(requested);
            } else {
                throw new RemoteTestException("Can't request more rows, processing shall be finished now.");
            }
        }

    }

    /**
     * Background thread with source data verification.
     */
    private static final class SourceDataThread implements Runnable {

        private final ServerResponse response;
        private final DbClient dbClient;

        private SourceDataThread(
                final ServerResponse response,
                final DbClient dbClient) {
            this.response = response;
            this.dbClient = dbClient;
        }

        @Override
        public void run() {
            try {
                Multi<DbRow> rows = dbClient.execute(exec -> exec
                        .namedQuery("select-types"));
                if (rows == null) {
                    throw new RemoteTestException("Rows value is null.");
                }
                List<DbRow> list = rows.collectList().await();
                if (list.isEmpty()) {
                    throw new RemoteTestException("Rows list is empty.");
                }
                if (list.size() != 18) {
                    throw new RemoteTestException("Rows list size shall be 18.");
                }
                list.forEach(row -> {
                    Integer id = row.column(1).as(Integer.class);
                    String name = row.column(2).as(String.class);
                    final Type type = new Type(id, name);
                    if (!Type.TYPES.get(id).getName().equals(name)) {
                        throw new RemoteTestException(
                                String.format(
                                        "Excpected type name \"%s\", but got \"%s\".",
                                        Type.TYPES.get(id).getName(),
                                        name));
                    }
                    LOGGER.log(Level.DEBUG, type::toString);
                });
                response.send(AppResponse.okStatus(JsonObject.EMPTY_JSON_OBJECT));
            } catch (RemoteTestException ex) {
                LOGGER.log(Level.DEBUG, "Sending error response.");
                response.send(exceptionStatus(ex));
            }
        }

    }

    // Source data verification test.
    // Testing code is blocking so it's running in a separate thread.
    private void testSourceData(final ServerRequest request, final ServerResponse response) {
        Thread thread = new Thread(new SourceDataThread(response, dbClient()));
        thread.start();
    }

    /**
     * Background thread with flow control evaluation.
     */
    private static final class FlowControlThread implements Runnable {

        private final ServerResponse response;
        private final DbClient dbClient;

        private FlowControlThread(
                final ServerResponse response,
                final DbClient dbClient) {
            this.response = response;
            this.dbClient = dbClient;
        }

        @Override
        @SuppressWarnings({"SleepWhileInLoop", "BusyWait", "SynchronizationOnLocalVariableOrMethodParameter"})
        public void run() {
            try {
                TestSubscriber subscriber = new TestSubscriber();
                Multi<DbRow> rows = dbClient.execute(exec -> exec
                        .namedQuery("select-types"));

                rows.subscribe(subscriber);
                while (!subscriber.finished) {
                    synchronized (subscriber) {
                        try {
                            subscriber.wait(20000);
                        } catch (InterruptedException ex) {
                            throw new RemoteTestException(String.format("Test failed with exception: %s", ex.getMessage()));
                        }
                    }
                    if (subscriber.canRequestNext()) {
                        // Small delay before requesting next records to see whether some unexpected will come
                        Thread.sleep(500);
                        subscriber.requestNext();
                    } else {
                        LOGGER.log(Level.DEBUG, "All requests were already done.");
                    }
                }
                if (subscriber.error != null) {
                    throw new RemoteTestException(subscriber.error);
                }
                LOGGER.log(Level.DEBUG, "Sending OK response.");
                JsonObjectBuilder job = Json.createObjectBuilder();
                job.add("requested", subscriber.requested);
                job.add("remaining", subscriber.remaining);
                job.add("total", subscriber.total);
                response.send(AppResponse.okStatus(job.build()));
            } catch (RemoteTestException | InterruptedException ex) {
                LOGGER.log(Level.DEBUG, "Sending error response.", ex);
                response.send(exceptionStatus(ex));
            }
        }

    }

    // Flow control test.
    // Testing code is blocking so it's running in a separate thread.
    private void testFlowControl(ServerRequest request, ServerResponse response) {
        Thread thread = new Thread(new FlowControlThread(response, dbClient()));
        thread.start();
    }

}
