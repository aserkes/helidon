/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http;

import java.util.EnumMap;
import java.util.Map;

import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.DirectHandler.EventType;
import io.helidon.common.http.Http;
import io.helidon.common.http.RequestException;
import io.helidon.nima.webserver.CloseConnectionException;

import static java.lang.System.Logger.Level.WARNING;

/**
 * Configured handlers for expected (and internal) exceptions.
 */
public class DirectHandlers {
    private static final System.Logger LOGGER = System.getLogger(DirectHandlers.class.getName());

    private final Map<EventType, DirectHandler> handlers;

    private DirectHandlers(Map<EventType, DirectHandler> handlers) {
        this.handlers = new EnumMap<>(handlers);
    }

    /**
     * New builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new instance with default handlers.
     *
     * @return a new instance
     */
    public static DirectHandlers create() {
        return builder().build();
    }

    /**
     * Get handler for the event type.
     * If no custom handler is defined, the default handler will be returned.
     *
     * @param eventType event type
     * @return handler to use
     */
    public DirectHandler handler(EventType eventType) {
        return handlers.get(eventType);
    }

    /**
     * Handle an HTTP Exception that occurred when request and response is available.
     *
     * @param httpException exception to handle
     * @param res           response
     * @param keepAlive     whether to keep the connection alive
     */
    public void handle(RequestException httpException, ServerResponse res, boolean keepAlive) {
        DirectHandler.TransportResponse response = handler(httpException.eventType()).handle(
                httpException.request(),
                httpException.eventType(),
                httpException.status(),
                httpException.responseHeaders(),
                httpException);

        Http.Status usedStatus;

        res.status(response.status());
        response.headers()
                .forEach(res::header);
        if (!keepAlive) {
            res.header(Http.HeaderValues.CONNECTION_CLOSE);
        }

        if (res.isSent()) {
            throw new CloseConnectionException(
                    "Cannot send response of a simple handler, status and headers already written");
        }

        try {
            response.entity().ifPresentOrElse(res::send, res::send);
        } catch (IllegalStateException ex) {
            // failed to send - probably output stream was already obtained and used, so status is written
            // we can only close the connection now
            res.streamResult(response.entity().map(String::new).orElseGet(() -> httpException.getCause().getMessage()));
            throw new CloseConnectionException(
                    "Cannot send response of a simple handler, status and headers already written",
                    ex);
        }

        usedStatus = response.status();

        if (usedStatus == Http.Status.INTERNAL_SERVER_ERROR_500) {
            LOGGER.log(WARNING, "Internal server error", httpException);
        }
    }

    /**
     * Fluent API builder for {@link DirectHandlers}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, DirectHandlers> {
        private final Map<EventType, DirectHandler> handlers = new EnumMap<>(EventType.class);
        private final DirectHandler defaultHandler = DirectHandler.defaultHandler();

        private Builder() {
        }

        @Override
        public DirectHandlers build() {
            for (EventType value : EventType.values()) {
                handlers.putIfAbsent(value, defaultHandler);
            }
            return new DirectHandlers(handlers);
        }

        /**
         * Add a handler.
         *
         * @param eventType event type to handle
         * @param handler   handler to handle that type
         * @return updated builder
         */
        public Builder addHandler(EventType eventType, DirectHandler handler) {
            handlers.put(eventType, handler);
            return this;
        }

        /**
         * Add defaults for even types not supported by the created handlers.
         *
         * @param handlers handlers to use as defaults
         * @return updated builder
         */
        public Builder defaults(DirectHandlers handlers) {
            handlers.handlers.forEach(this.handlers::putIfAbsent);
            return this;
        }
    }
}
