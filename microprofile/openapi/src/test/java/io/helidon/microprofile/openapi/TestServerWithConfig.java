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
package io.helidon.microprofile.openapi;

import java.net.HttpURLConnection;
import java.util.Map;

import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.ClasspathConfigSource;
import io.helidon.config.Config;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class TestServerWithConfig {

    private static final String ALTERNATE_OPENAPI_PATH = "/otheropenapi";

    private static Server server;

    private static HttpURLConnection cnx;

    private static Map<String, Object> yaml;

    public TestServerWithConfig() {
    }

    @BeforeAll
    public static void startServer() throws Exception {
        Config helidonConfig = Config.builder().addSource(ClasspathConfigSource.create("/serverConfig.yml")).build();
        server = TestUtil.startServer(helidonConfig, TestApp.class);
        cnx = TestUtil.getURLConnection(
                server.port(),
                "GET",
                ALTERNATE_OPENAPI_PATH,
                HttpMediaType.create(MediaTypes.APPLICATION_OPENAPI_YAML));
        yaml = TestUtil.yamlFromResponse(cnx);
    }

    @AfterAll
    public static void stopServer() {
        TestUtil.cleanup(server, cnx);
    }

    @Test
    public void testAlternatePath() throws Exception {
        String goSummary = TestUtil.fromYaml(yaml, "paths./testapp/go.get.summary", String.class);
        assertThat(goSummary, is(TestApp.GO_SUMMARY));
    }
}
