/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.translator.frontend;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.BadRequestException;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.reactive.webserver.Service;
import io.helidon.tracing.jersey.client.ClientTracingFilter;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

/**
 * Translator frontend resource.
 */
public final class TranslatorFrontendService implements Service {

    private static final Logger LOGGER = Logger.getLogger(TranslatorFrontendService.class.getName());
    private final WebTarget backendTarget;

    TranslatorFrontendService(String backendHostname, int backendPort) {
         backendTarget = ClientBuilder.newClient()
                 .target("http://" + backendHostname + ":" + backendPort);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get(this::getText);
    }

    private void getText(ServerRequest request, ServerResponse response) {
        try {
            String query = request.queryParams().first("q")
                    .orElseThrow(() -> new BadRequestException("missing query parameter 'q'"));
            String language = request.queryParams().first("lang")
                    .orElseThrow(() -> new BadRequestException("missing query parameter 'lang'"));

            try (Response backendResponse = backendTarget
                    .property(ClientTracingFilter.TRACER_PROPERTY_NAME, request.tracer())
                    .property(ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME, request.spanContext().orElse(null))
                    .queryParam("q", query)
                    .queryParam("lang", language)
                    .request()
                    .get()) {
                final String result;
                if (backendResponse.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                    result = backendResponse.readEntity(String.class);
                } else {
                    result = "Error: " + backendResponse.readEntity(String.class);
                }
                response.send(result + "\n");
            }
        } catch (ProcessingException pe) {
            LOGGER.log(Level.WARNING, "Problem to call translator frontend.", pe);
            response.status(503).send("Translator backend service isn't available.");
        }
    }
}
