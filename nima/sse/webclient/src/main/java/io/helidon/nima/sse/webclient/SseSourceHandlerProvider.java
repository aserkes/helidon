/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.sse.webclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.sse.SseEvent;
import io.helidon.nima.webclient.ClientResponse;
import io.helidon.nima.webclient.http.spi.Source;
import io.helidon.nima.webclient.http.spi.SourceHandlerProvider;

/**
 * A handler for SSE sources.
 */
public class SseSourceHandlerProvider implements SourceHandlerProvider<SseEvent> {

    private static final char BOM = 0xFEFF;
    private static final String ID = "id:";
    private static final String DATA = "data:";
    private static final String RETRY = "retry:";
    private static final String EVENT = "event:";

    @Override
    public boolean supports(GenericType<? extends Source<?>> type, ClientResponse response) {
        return SseSource.TYPE.equals(type) && response.headers().contentType()
                .map(ct -> ct.mediaType().equals(MediaTypes.TEXT_EVENT_STREAM)).orElse(false);
    }

    @Override
    public <X extends Source<SseEvent>> void handle(X source, ClientResponse response, MediaContext mediaContext) {
        InputStream is = response.inputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean emit = false;
            boolean first = true;
            StringBuilder data = new StringBuilder();
            SseEvent.Builder sseBuilder = SseEvent.builder().mediaContext(mediaContext);

            source.onOpen();

            while ((line = reader.readLine()) != null) {
                if (first && line.charAt(0) == BOM) {       // optional BOM
                    line = line.substring(1);
                }
                first = false;
                if (line.isBlank()) {
                    if (emit) {
                        sseBuilder.data(data.toString());
                        source.onEvent(sseBuilder.build());
                        data.setLength(0);
                        sseBuilder = SseEvent.builder();
                        emit = false;
                    }
                    continue;
                }
                emit = true;
                if (line.startsWith(DATA)) {
                    data.append(skipPrefix(line));
                } else if (line.startsWith(EVENT)) {
                    sseBuilder.name(skipPrefix(line));
                } else if (line.startsWith(ID)) {
                    sseBuilder.id(skipPrefix(line));
                } else if (line.startsWith(RETRY)) {
                    long delay = Long.parseLong(skipPrefix(line));
                    sseBuilder.reconnectDelay(Duration.ofMillis(delay));
                } else if (line.startsWith(":")) {
                    sseBuilder.comment(line.length() > 1 ? line.substring(1) : "");
                } else {
                    emit = false;
                }
            }

            source.onClose();
        } catch (IOException e) {
            source.onError(e);
            throw new UncheckedIOException(e);
        } catch (NumberFormatException e) {
            source.onError(e);
            throw e;
        }
    }

    private static String skipPrefix(String line) {
        StringBuilder builder = new StringBuilder(line.length());
        int state = 0;
        for (int i = 0; i < line.length(); i++) {
            switch (state) {
                case 0:
                    if (line.charAt(i) == ':') {
                        state = 1;      // found delimiter
                    }
                    break;
                case 1:
                    char ch = line.charAt(i);
                    if (ch != ' ') {
                        builder.append(ch);
                        state = 2;      // stop skipping spaces
                    }
                    break;
                case 2:
                    builder.append(line.charAt(i));
                    break;
                default:
                    throw new IllegalStateException("Illegal SSE parser state for text/event-stream");
            }
        }
        return builder.toString();
    }
}
