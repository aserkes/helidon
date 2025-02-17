/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.reactive.dbclient.mongodb;

import java.lang.System.Logger.Level;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.reactive.dbclient.DbRow;
import io.helidon.reactive.dbclient.common.DbClientContext;

import org.bson.Document;
import org.reactivestreams.Subscription;

/**
 * Mongo specific query asynchronous processor.
 */
final class MongoDbQueryProcessor implements org.reactivestreams.Subscriber<Document>, Flow.Publisher<DbRow>, Flow.Subscription {

    /** Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(MongoDbQueryProcessor.class.getName());

    private final AtomicLong count = new AtomicLong();
    private final CompletableFuture<Long> queryFuture;
    private final MongoDbStatement dbStatement;
    private final CompletableFuture<Void> statementFuture;
    private final DbClientContext clientContext;

    private Flow.Subscriber<? super DbRow> subscriber;
    private Subscription subscription;

    MongoDbQueryProcessor(DbClientContext clientContext,
                          MongoDbStatement dbStatement,
                          CompletableFuture<Void> statementFuture,
                          CompletableFuture<Long> queryFuture) {
        this.clientContext = clientContext;
        this.statementFuture = statementFuture;
        this.queryFuture = queryFuture;
        this.dbStatement = dbStatement;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
    }

    @Override
    public void onNext(Document doc) {
        MongoDbRow dbRow = new MongoDbRow(clientContext.dbMapperManager(), clientContext.mapperManager(), doc.size());
        doc.forEach((name, value) -> {
            LOGGER.log(Level.TRACE, () -> String.format(
                    "Column name = %s, value = %s", name, (value != null ? value.toString() : "N/A")));
            dbRow.add(name, new MongoDbColumn(clientContext.dbMapperManager(), clientContext.mapperManager(), name, value));
        });
        count.incrementAndGet();
        subscriber.onNext(dbRow);
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.log(Level.TRACE, () -> String.format("Query error: %s", t.getMessage()));
        statementFuture.completeExceptionally(t);
        queryFuture.completeExceptionally(t);
        if (dbStatement.txManager() != null) {
            dbStatement.txManager().stmtFailed(dbStatement);
        }
        subscriber.onError(t);
        LOGGER.log(Level.TRACE, () -> String.format("Query %s execution failed", dbStatement.statementName()));
    }

    @Override
    public void onComplete() {
        LOGGER.log(Level.TRACE, () -> "Query finished");
        statementFuture.complete(null);
        queryFuture.complete(count.get());
        if (dbStatement.txManager() != null) {
            dbStatement.txManager().stmtFinished(dbStatement);
        }
        subscriber.onComplete();
        LOGGER.log(Level.TRACE, () -> String.format("Query %s execution succeeded", dbStatement.statementName()));
    }

    @Override
    public void subscribe(Flow.Subscriber<? super DbRow> subscriber) {
        this.subscriber = subscriber;
        LOGGER.log(Level.TRACE, () -> "Calling onSubscribe on subscriber");
        subscriber.onSubscribe(this);
    }

    @Override
    public void request(long n) {
        LOGGER.log(Level.TRACE, () -> String.format("Requesting %d records from MongoDB", n));
        this.subscription.request(n);
    }

    @Override
    public void cancel() {
        LOGGER.log(Level.TRACE, () -> "Cancelling MongoDB result processing");
        this.subscription.cancel();
    }

}
