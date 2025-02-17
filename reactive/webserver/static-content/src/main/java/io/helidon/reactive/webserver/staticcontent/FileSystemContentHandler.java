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

package io.helidon.reactive.webserver.staticcontent;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.common.http.Http;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;

/**
 * Serves files from the filesystem as a static WEB content.
 */
class FileSystemContentHandler extends FileBasedContentHandler {
    private static final System.Logger LOGGER = System.getLogger(FileSystemContentHandler.class.getName());

    private final Path root;

    FileSystemContentHandler(StaticContentSupport.FileSystemBuilder builder) {
        super(builder);

        this.root = builder.root();
    }

    @Override
    boolean doHandle(Http.Method method, String requestedPath, ServerRequest request, ServerResponse response)
            throws IOException {
        Path resolved;
        if (requestedPath.isEmpty()) {
            resolved = root;
        } else {
            resolved = root.resolve(requestedPath).normalize();
            LOGGER.log(Level.TRACE, () -> "Requested file: " + resolved.toAbsolutePath());
            if (!resolved.startsWith(root)) {
                return false;
            }
        }

        return doHandle(method, resolved, request, response);
    }

    boolean doHandle(Http.Method method, Path path, ServerRequest request, ServerResponse response) throws IOException {
        // Check existence
        if (!Files.exists(path)) {
            return false;
        }

        sendFile(method, path, request, response, welcomePageName());

        return true;
    }

}
