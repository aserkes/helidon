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
package io.helidon.lra.coordinator;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.scheduling.FixedRateInvocation;
import io.helidon.scheduling.Scheduling;
import io.helidon.scheduling.Task;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static io.helidon.common.http.Http.Status.CREATED_201;
import static io.helidon.common.http.Http.Status.GONE_410;
import static io.helidon.common.http.Http.Status.NOT_FOUND_404;
import static io.helidon.common.http.Http.Status.OK_200;
import static io.helidon.common.http.Http.Status.PRECONDITION_FAILED_412;

/**
 * LRA coordinator with Narayana like rest api.
 */
public class CoordinatorService implements HttpService {

    /**
     * Configuration prefix.
     */
    public static final String CONFIG_PREFIX = "helidon.lra.coordinator";
    static final String CLIENT_ID_PARAM_NAME = "ClientID";
    static final String TIME_LIMIT_PARAM_NAME = "TimeLimit";
    static final String PARENT_LRA_PARAM_NAME = "ParentLRA";
    static final String COORDINATOR_URL_KEY = "url";
    static final String DEFAULT_COORDINATOR_URL = "http://localhost:8070/lra-coordinator";

    private static final System.Logger LOGGER = System.getLogger(CoordinatorService.class.getName());
    private static final Http.HeaderName LRA_HTTP_CONTEXT_HEADER = Http.Header.create(LRA.LRA_HTTP_CONTEXT_HEADER);
    private static final Http.HeaderName LRA_HTTP_RECOVERY_HEADER = Http.Header.create(LRA.LRA_HTTP_RECOVERY_HEADER);

    private static final Set<LRAStatus> RECOVERABLE_STATUSES = Set.of(LRAStatus.Cancelling, LRAStatus.Closing, LRAStatus.Active);
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private final AtomicReference<CompletableFuture<Void>> completedRecovery = new AtomicReference<>(new CompletableFuture<>());

    private final LraPersistentRegistry lraPersistentRegistry;

    private final LazyValue<URI> coordinatorURL;
    private final Config config;
    private Task recoveryTask;
    private Task persistTask = null;
    private volatile boolean shuttingDown = false;

    CoordinatorService(LraPersistentRegistry lraPersistentRegistry, Supplier<URI> coordinatorUriSupplier, Config config) {
        this.lraPersistentRegistry = lraPersistentRegistry;
        coordinatorURL = LazyValue.create(coordinatorUriSupplier);
        this.config = config;
        init();
    }

    private void init() {
        lraPersistentRegistry.load(this);
        recoveryTask = Scheduling.fixedRateBuilder()
                .delay(config.get("recovery-interval").asLong().orElse(200L))
                .initialDelay(200)
                .timeUnit(TimeUnit.MILLISECONDS)
                .task(this::tick)
                .build();

        if (config.get("periodical-persist").asBoolean().orElse(false)) {
            persistTask = Scheduling.fixedRateBuilder()
                    .delay(config.get("persist-interval").asLong().orElse(5000L))
                    .initialDelay(200)
                    .timeUnit(TimeUnit.MILLISECONDS)
                    .task(inv -> lraPersistentRegistry.save())
                    .build();
        }
    }

    /**
     * Gracefully shutdown coordinator.
     */
    public void shutdown() {
        shuttingDown = true;
        Stream.of(recoveryTask, persistTask)
                .filter(Objects::nonNull)
                .forEach(task -> {
                    task.executor().shutdown();
                    try {
                        if (!task.executor().awaitTermination(5, TimeUnit.SECONDS)) {
                            LOGGER.log(Level.WARNING, "Shutdown of the scheduled task took too long.");
                        }
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.WARNING, "Shutdown of the scheduled task was interrupted.", e);
                    }
                });
        lraPersistentRegistry.save();
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/", this::get)
                .get("/recovery", this::recovery)
                .get("/{LraId}/recovery", this::recovery)
                .post("/start", this::start)
                .put("/{LraId}/close", this::close)
                .put("/{LraId}/cancel", this::cancel)
                .put("/{LraId}", this::join)
                .get("/{LraId}", this::get)
                .get("/{LraId}/status", this::status)
                .put("/{LraId}/remove", this::leave);
    }

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void start(ServerRequest req, ServerResponse res) {

        long timeLimit = req.query().first(TIME_LIMIT_PARAM_NAME).map(Long::valueOf).orElse(0L);
        String parentLRA = req.query().first(PARENT_LRA_PARAM_NAME).orElse("");

        String lraUUID = UUID.randomUUID().toString();
        URI lraId = coordinatorUriWithPath(lraUUID);
        if (!parentLRA.isEmpty()) {
            Lra parent = lraPersistentRegistry.get(parentLRA.replace(coordinatorURL.get().toASCIIString() + "/", ""));
            if (parent != null) {
                Lra childLra = new Lra(this, lraUUID, URI.create(parentLRA), this.config);
                childLra.setupTimeout(timeLimit);
                lraPersistentRegistry.put(lraUUID, childLra);
                parent.addChild(childLra);
            }
        } else {
            Lra newLra = new Lra(this, lraUUID, config);
            newLra.setupTimeout(timeLimit);
            lraPersistentRegistry.put(lraUUID, newLra);
        }

        res.headers().add(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString());
        res.status(CREATED_201)
                .send(lraId.toString());
    }

    /**
     * Close LRA if its active. Should cause coordinator to complete its participants.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void close(ServerRequest req, ServerResponse res) {
        String lraId = req.path().pathParameters().value("LraId");
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
            return;
        }
        if (lra.status().get() != LRAStatus.Active) {
            // Already time-outed
            res.status(GONE_410).send();
            return;
        }
        lra.close();
        res.status(OK_200).send();
    }

    /**
     * Cancel LRA if its active. Should cause coordinator to compensate its participants.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void cancel(ServerRequest req, ServerResponse res) {
        String lraId = req.path().pathParameters().value("LraId");
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
            return;
        }
        lra.cancel();
        res.status(OK_200).send();
    }

    /**
     * Join existing LRA with participant.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void join(ServerRequest req, ServerResponse res) {

        String lraId = req.path().pathParameters().value("LraId");
        String compensatorLink = req.headers().first(Http.Header.LINK).orElse("");

        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
            return;
        } else if (lra.checkTimeout()) {
            // too late to join
            res.status(PRECONDITION_FAILED_412).send();
            return;
        }
        lra.addParticipant(compensatorLink);
        String recoveryUrl = coordinatorUriWithPath("/" + lraId + "/recovery").toASCIIString();

        res.headers().set(LRA_HTTP_RECOVERY_HEADER, recoveryUrl);
        res.headers().set(Http.Header.LOCATION, recoveryUrl);
        res.status(OK_200)
                .send(recoveryUrl);
    }

    /**
     * Return status of specified LRA.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void status(ServerRequest req, ServerResponse res) {
        String lraId = req.path().pathParameters().value("LraId");
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
            return;
        }

        res.status(OK_200)
                .send(lra.status().get().name());
    }

    /**
     * Leave LRA. Supplied participant won't be part of specified LRA any more,
     * no compensation or completion will be executed on it.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void leave(ServerRequest req, ServerResponse res) {
        String lraId = req.path().pathParameters().value("LraId");
        String compensatorLinks = req.content().as(String.class);

        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
        } else {
            lra.removeParticipant(compensatorLinks);
            res.status(OK_200).send();
        }
    }

    /**
     * Blocks until next recovery cycle is finished.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void recovery(ServerRequest req, ServerResponse res) {
        nextRecoveryCycle().await();

        Optional<String> lraUUID = req.query().first("lraId")
                .or(() -> req.path().pathParameters().first("LraId"))
                .map(l -> {
                    if (l.lastIndexOf("/") != -1 && l.lastIndexOf("/") + 1 < l.length()) {
                        return l.substring(l.lastIndexOf("/") + 1);
                    } else {
                        return l;
                    }
                });

        if (lraUUID.isPresent()) {
            Lra lra = lraPersistentRegistry.get(lraUUID.get());
            if (lra != null) {
                if (RECOVERABLE_STATUSES.contains(lra.status().get())) {
                    JsonObject json = JSON.createObjectBuilder()
                            .add("lraId", lra.lraId())
                            .add("status", lra.status().get().name())
                            .add("recovering", Set.of(LRAStatus.Closed, LRAStatus.Cancelled).contains(lra.status().get()))
                            .build();
                    res.status(OK_200).send(json);
                } else {
                    res.status(OK_200).send(JsonValue.EMPTY_JSON_OBJECT);
                }
            } else {
                res.status(NOT_FOUND_404).send(JsonValue.EMPTY_JSON_OBJECT);
            }
        } else {
            JsonArray jsonValues = lraPersistentRegistry
                    .stream()
                    .filter(lra -> RECOVERABLE_STATUSES.contains(lra.status().get()))
                    .map(l -> JSON.createObjectBuilder()
                            .add("lraId", l.lraId())
                            .add("status", l.status().get().name())
                            .build()
                    )
                    .collect(JSON::createArrayBuilder, JsonArrayBuilder::add)
                    .await()
                    .build();

            res.status(OK_200).send(jsonValues);
        }
    }

    private void get(ServerRequest req, ServerResponse res) {
        Optional<String> lraId = req.path().pathParameters().first("LraId")
                .or(() -> req.query().first("lraId"));

        lraPersistentRegistry
                .stream()
                // filter by lraId param or dont filter at all
                .filter(lra -> lraId.map(id -> lra.lraId().equals(id)).orElse(true))
                .map(l -> JSON.createObjectBuilder()
                        .add("lraId", l.lraId())
                        .add("status", l.status().get().name())
                        .build()
                )
                .collect(JSON::createArrayBuilder, JsonArrayBuilder::add)
                .map(JsonArrayBuilder::build)
                .onError(res::send)
                .defaultIfEmpty(JsonArray.EMPTY_JSON_ARRAY)
                .forSingle(s -> res.status(OK_200).send(s));
    }

    private void tick(FixedRateInvocation inv) {
        if (shuttingDown) {
            return;
        }
        lraPersistentRegistry.stream().forEach(lra -> {
            if (shuttingDown) {
                return;
            }
            if (lra.isReadyToDelete()) {
                lraPersistentRegistry.remove(lra.lraId());
            } else {
                if (LRAStatus.Cancelling == lra.status().get()) {
                    LOGGER.log(Level.DEBUG, "Recovering {0}", lra.lraId());
                    lra.cancel();
                }
                if (LRAStatus.Closing == lra.status().get()) {
                    LOGGER.log(Level.DEBUG, "Recovering {0}", lra.lraId());
                    lra.close();
                }
                if (lra.checkTimeout() && lra.status().get().equals(LRAStatus.Active)) {
                    LOGGER.log(Level.DEBUG, "Timeouting {0} ", lra.lraId());
                    lra.timeout();
                }
                if (Set.of(LRAStatus.Closed, LRAStatus.Cancelled).contains(lra.status().get())) {
                    // If a participant is unable to complete or compensate immediately or because of a failure
                    // then it must remember the fact (by reporting its' status via the @Status method)
                    // until explicitly told that it can clean up using this @Forget annotation.
                    LOGGER.log(Level.DEBUG, "Forgetting {0} {1}", new Object[] {lra.status().get(), lra.lraId()});
                    lra.tryForget();
                    lra.tryAfter();
                }
            }
        });
        completedRecovery.getAndSet(new CompletableFuture<>()).complete(null);
    }

    LazyValue<URI> getCoordinatorURL() {
        return coordinatorURL;
    }

    private Single<Void> nextRecoveryCycle() {
        return Single.create(completedRecovery.get(), true)
                //wait for the second one, as first could have been in progress
                .onCompleteResumeWith(Single.create(completedRecovery.get(), true))
                .ignoreElements();
    }

    private URI coordinatorUriWithPath(String additionalPath) {
        return URI.create(coordinatorURL.get().toASCIIString() + "/" + additionalPath);
    }

    /**
     * Create a new Lra coordinator.
     *
     * @return coordinator
     */
    public static CoordinatorService create() {
        return builder()
                .config(Config.create().get(CoordinatorService.CONFIG_PREFIX))
                .build();
    }

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Coordinator builder.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, CoordinatorService> {

        private Config config;
        private LraPersistentRegistry lraPersistentRegistry;
        private Supplier<URI> uriSupplier = () -> URI.create(config.get(COORDINATOR_URL_KEY)
                .asString()
                .orElse(DEFAULT_COORDINATOR_URL));

        /**
         * Configuration needed for configuring coordinator.
         *
         * @param config config for Lra coordinator.
         * @return this builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Custom persistent registry for saving and loading the state of the coordinator.
         * Coordinator is not persistent by default.
         *
         * @param lraPersistentRegistry custom persistent registry
         * @return this builder
         */
        public Builder persistentRegistry(LraPersistentRegistry lraPersistentRegistry) {
            this.lraPersistentRegistry = lraPersistentRegistry;
            return this;
        }

        /**
         * Supplier for coordinator url.
         * For supplying url after we know the port of the started server.
         *
         * @param uriSupplier coordinator url
         * @return this builder
         */
        public Builder url(Supplier<URI> uriSupplier) {
            this.uriSupplier = uriSupplier;
            return this;
        }

        @Override
        public CoordinatorService build() {
            if (config == null) {
                config = Config.create().get(CoordinatorService.CONFIG_PREFIX);
            }
            if (lraPersistentRegistry == null) {
                lraPersistentRegistry = new LraDatabasePersistentRegistry(config);
            }
            return new CoordinatorService(lraPersistentRegistry, uriSupplier, config);
        }
    }
}
