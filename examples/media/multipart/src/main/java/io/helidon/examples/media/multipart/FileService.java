/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.media.multipart;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.http.BadRequestException;
import io.helidon.common.http.ContentDisposition;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.reactive.IoMulti;
import io.helidon.reactive.media.multipart.ReadableBodyPart;
import io.helidon.reactive.webserver.ResponseHeaders;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.reactive.webserver.Service;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;

/**
 * File service.
 */
public final class FileService implements Service {

    private static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of());
    private final FileStorage storage;
    private final ExecutorService executor = ThreadPoolSupplier.create("multipart-thread-pool").get();


    /**
     * Create a new file upload service instance.
     */
    FileService() {
        storage = new FileStorage();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::list)
                .get("/{fname}", this::download)
                .post("/", this::upload);
    }

    private void list(ServerRequest req, ServerResponse res) {
        JsonArrayBuilder arrayBuilder = JSON_FACTORY.createArrayBuilder();
        storage.listFiles().forEach(arrayBuilder::add);
        res.send(JSON_FACTORY.createObjectBuilder().add("files", arrayBuilder).build());
    }

    private void download(ServerRequest req, ServerResponse res) {
        Path filePath = storage.lookup(req.path().param("fname"));
        ResponseHeaders headers = res.headers();
        headers.contentType(MediaTypes.APPLICATION_OCTET_STREAM);
        headers.set(Http.Header.CONTENT_DISPOSITION, ContentDisposition.builder()
                .filename(filePath.getFileName().toString())
                .build()
                .toString());
        res.send(filePath);
    }

    private void upload(ServerRequest req, ServerResponse res) {
        req.content().asStream(ReadableBodyPart.class)
           .forEach(part -> {
               if (part.isNamed("file[]")) {
                   String filename = part.filename()
                                         .orElseThrow(() -> new BadRequestException("Missing filename"));
                   part.content()
                       .map(DataChunk::data)
                       .flatMapIterable(Arrays::asList)
                       .to(IoMulti.writeToFile(storage.create(filename))
                                  .executor(executor)
                                  .build());
               } else {
                   // when streaming unconsumed parts needs to be drained
                   part.drain();
               }
           })
           .onError(res::send)
           .onComplete(() -> {
               res.status(Http.Status.MOVED_PERMANENTLY_301);
               res.headers().set(Http.Header.LOCATION, "/ui");
               res.send();
           }).ignoreElement();
    }
}
