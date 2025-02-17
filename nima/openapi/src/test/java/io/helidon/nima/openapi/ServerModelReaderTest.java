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
package io.helidon.nima.openapi;

import java.net.HttpURLConnection;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.nima.openapi.test.MyModelReader;
import io.helidon.nima.webserver.WebServer;

import jakarta.json.JsonException;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Makes sure that the app-supplied model reader participates in constructing
 * the OpenAPI model.
 */
public class ServerModelReaderTest {

    private static final String SIMPLE_PROPS_PATH = "/openapi";

    private static final OpenApiService.Builder OPENAPI_SUPPORT_BUILDER =
            OpenApiService.builder()
                    .config(Config.create(ConfigSources.classpath("simple.properties")).get(OpenApiService.Builder.CONFIG_KEY));

    private static WebServer webServer;

    @BeforeAll
    public static void startup() {
        webServer = TestUtil.startServer(OPENAPI_SUPPORT_BUILDER);
    }

    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(webServer);
    }

    @Test
    @Disabled
    public void checkCustomModelReader() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                webServer.port(),
                "GET",
                SIMPLE_PROPS_PATH,
                MediaTypes.APPLICATION_OPENAPI_JSON);
        TestUtil.validateResponseMediaType(cnx, MediaTypes.APPLICATION_OPENAPI_JSON);
        JsonStructure json = TestUtil.jsonFromResponse(cnx);
        // The model reader adds the following key/value (among others) to the model.
        JsonValue v = json.getValue(String.format("/paths/%s/get/summary",
                                                  TestUtil.escapeForJsonPointer(MyModelReader.MODEL_READER_PATH)));
        if (v.getValueType().equals(JsonValue.ValueType.STRING)) {
            JsonString s = (JsonString) v;
            assertEquals(MyModelReader.SUMMARY, s.getString(),
                         "Unexpected summary value as added by model reader");
        }
    }

    @Test
    public void makeSureFilteredPathIsMissing() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                webServer.port(),
                "GET",
                SIMPLE_PROPS_PATH,
                MediaTypes.APPLICATION_OPENAPI_JSON);
        TestUtil.validateResponseMediaType(cnx, MediaTypes.APPLICATION_OPENAPI_JSON);
        JsonStructure json = TestUtil.jsonFromResponse(cnx);
        /*
         * Although the model reader adds this path, the filter should have
         * removed it.
         */
        final JsonException ex = assertThrows(
                JsonException.class,
                () -> {
                    JsonValue v = json.getValue(String.format("/paths/%s/get/summary",
                                                              TestUtil.escapeForJsonPointer(MyModelReader.DOOMED_PATH)));
                });
        assertTrue(ex.getMessage().contains(
                String.format("contains no mapping for the name '%s'", MyModelReader.DOOMED_PATH)));
    }
}
