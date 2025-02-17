/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.mp.httpstatuscount;

import io.helidon.common.http.Http;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Test-only resource that allows the client to specify what HTTP status the service should return in its response.
 * This allows the client to know which status family counter should be updated.
 */
@RequestScoped
@Path("/status")
public class StatusResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/{status}")
    public Response reportStatus(@PathParam("status") String statusText) {
        int status;
        String msg;
        try {
            status = Integer.parseInt(statusText);
            msg = "Successful conversion";
        } catch (NumberFormatException ex) {
            status = Http.Status.INTERNAL_SERVER_ERROR_500.code();
            msg = "Unsuccessful conversion";
        }
        return status == 204 ? Response.status(204).build() : Response.status(status).entity(msg).build();
    }
}
