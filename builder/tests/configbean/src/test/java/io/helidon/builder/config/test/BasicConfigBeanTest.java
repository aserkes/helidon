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

package io.helidon.builder.config.test;

import java.util.List;
import java.util.Map;

import io.helidon.builder.config.spi.GeneratedConfigBean;
import io.helidon.builder.config.spi.MetaConfigBeanInfo;
import io.helidon.builder.config.testsubjects.TestClientConfigDefault;
import io.helidon.builder.config.testsubjects.TestServerConfigDefault;
import io.helidon.builder.config.testsubjects.TestClientConfig;
import io.helidon.builder.config.testsubjects.TestServerConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.yaml.YamlConfigParser;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;

class BasicConfigBeanTest {

    @Test
    void acceptConfig() {
        Config cfg = Config.builder(
                ConfigSources.create(
                        Map.of("name", "server",
                               "port", "8080",
                               "description", "test",
                               "pswd", "pwd1",
                               "cipher-suites.0", "a",
                               "cipher-suites.1", "b",
                               "cipher-suites.2", "c",
                               "headers.0", "header1",
                               "headers.1", "header2"),
                        "my-simple-config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        TestServerConfig serverConfig = TestServerConfigDefault.toBuilder(cfg).build();
        assertThat(serverConfig.description(),
                   optionalValue(equalTo("test")));
        assertThat(serverConfig.name(),
                   equalTo("server"));
        assertThat(serverConfig.port(),
                   equalTo(8080));
        assertThat(new String(serverConfig.pswd()),
                   equalTo("pwd1"));
        assertThat(serverConfig.toString(),
                   startsWith("TestServerConfig"));
        assertThat(serverConfig.cipherSuites(),
                   contains("a", "b", "c"));
        assertThat(serverConfig.toString(),
                   endsWith("(name=server, port=8080, cipherSuites=[a, b, c], pswd=not-null, description=Optional[test])"));
        GeneratedConfigBean generatedCB = (GeneratedConfigBean) serverConfig;
        assertThat(generatedCB.__name(),
                   optionalValue(equalTo("")));
        assertThat(generatedCB.__metaInfo(),
                   equalTo(MetaConfigBeanInfo.builder()
                                   .value("test-server")
                                   .repeatable(false)
                                   .drivesActivation(false)
                                   .atLeastOne(true)
                                   .wantDefaultConfigBean(false)
                                   .levelType(io.helidon.builder.config.ConfigBean.LevelType.ROOT)
                                   .build()));

        TestClientConfig clientConfig = TestClientConfigDefault.toBuilder(cfg).build();
        assertThat(clientConfig.name(),
                   equalTo("server"));
        assertThat(clientConfig.port(),
                   equalTo(8080));
        assertThat(new String(clientConfig.pswd()),
                   equalTo("pwd1"));
        assertThat(clientConfig.toString(),
                   startsWith("TestClientConfig"));
        assertThat(clientConfig.cipherSuites(),
                   contains("a", "b", "c"));
        assertThat(clientConfig.headers(),
                   hasEntry("headers.0", "header1"));
        assertThat(clientConfig.headers(),
                   hasEntry("headers.1", "header2"));
        assertThat(clientConfig.toString(),
                   endsWith("(name=server, port=8080, cipherSuites=[a, b, c], pswd=not-null, "
                                    + "serverPort=0, headers={headers.1=header2, headers.0=header1})"));
        generatedCB = (GeneratedConfigBean) clientConfig;
        assertThat(generatedCB.__name(),
                   optionalValue(equalTo("")));
        assertThat(generatedCB.__metaInfo(),
                   equalTo(MetaConfigBeanInfo.builder()
                                   .value("test-client")
                                   .repeatable(true)
                                   .drivesActivation(false)
                                   .atLeastOne(false)
                                   .wantDefaultConfigBean(false)
                                   .levelType(io.helidon.builder.config.ConfigBean.LevelType.ROOT)
                                   .build()));
    }

    @Test
    void emptyConfig() {
        Config cfg = Config.create();
        TestServerConfig serverConfig = TestServerConfigDefault.toBuilder(cfg).build();
        assertThat(serverConfig.description(),
                   optionalEmpty());
        assertThat(serverConfig.name(),
                   equalTo("default"));
        assertThat(serverConfig.port(),
                   equalTo(0));

        GeneratedConfigBean generatedCB = (GeneratedConfigBean) serverConfig;
        assertThat(generatedCB.__name(),
                   optionalValue(equalTo("")));
    }

    /**
     * Callers can conceptually use config beans as just plain old vanilla builders, void of any config usage.
     */
    @Test
    void noConfig() {
        TestServerConfig serverConfig = TestServerConfigDefault.builder().build();
        assertThat(serverConfig.description(), optionalEmpty());
        assertThat(serverConfig.name(),
                   equalTo("default"));
        assertThat(serverConfig.port(),
                   equalTo(0));
        assertThat(serverConfig.cipherSuites(),
                   equalTo(List.of()));

        serverConfig = TestServerConfigDefault.toBuilder(serverConfig).port(123).build();
        assertThat(serverConfig.description(),
                   optionalEmpty());
        assertThat(serverConfig.name(),
                   equalTo("default"));
        assertThat(serverConfig.port(),
                   equalTo(123));
        assertThat(serverConfig.cipherSuites(),
                   equalTo(List.of()));

        TestClientConfig clientConfig = TestClientConfigDefault.builder().build();
        assertThat(clientConfig.name(),
                   equalTo("default"));
        assertThat(clientConfig.port(),
                   equalTo(0));
        assertThat(clientConfig.headers(),
                   equalTo(Map.of()));
        assertThat(clientConfig.cipherSuites(),
                   equalTo(List.of()));

        clientConfig = TestClientConfigDefault.toBuilder(clientConfig).port(123).build();
        assertThat(clientConfig.name(),
                   equalTo("default"));
        assertThat(clientConfig.port(),
                   equalTo(123));
        assertThat(clientConfig.headers(),
                   equalTo(Map.of()));
        assertThat(clientConfig.cipherSuites(),
                   equalTo(List.of()));

        GeneratedConfigBean generatedCB = (GeneratedConfigBean) clientConfig;
        assertThat(generatedCB.__name(),
                   optionalEmpty());
    }

    @Test
    void equality() {
        Config cfg = Config.builder()
                .sources(ConfigSources.classpath("io/helidon/builder/config/test/basic-config-bean-test.yaml"))
                .addParser(YamlConfigParser.create())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        Config serverCfg = cfg.get("test-server");
        TestServerConfigDefault.Builder serverConfigBeanManualBuilder = TestServerConfigDefault.builder()
                .port(serverCfg.get("port").asInt().get());
        serverCfg.get("name").asString().ifPresent(serverConfigBeanManualBuilder::name);
        serverCfg.get("pswd").asString().ifPresent(serverConfigBeanManualBuilder::pswd);
        serverCfg.get("description").asString().ifPresent(serverConfigBeanManualBuilder::description);
        TestServerConfig serverConfigBeanManual = serverConfigBeanManualBuilder.build();

        Config clientCfg = cfg.get("test-client");
        TestClientConfigDefault.Builder clientConfigBeanManualBuilder = TestClientConfigDefault.builder()
                .port(clientCfg.get("port").asInt().get())
                .serverPort(clientCfg.get("server-port").asInt().get())
                .cipherSuites(clientCfg.get("cipher-suites").asList(String.class).get())
                .headers(clientCfg.get("headers").asMap().get());
        clientCfg.get("name").asString().ifPresent(clientConfigBeanManualBuilder::name);
        clientCfg.get("pswd").asString().ifPresent(serverConfigBeanManualBuilder::pswd);
        TestClientConfig clientConfigBeanManual = clientConfigBeanManualBuilder.build();

        // juxtaposed to the new ConfigBean approach
        TestServerConfig serverConfigBean = TestServerConfigDefault.toBuilder(serverCfg).build();
        TestClientConfig clientConfigBean = TestClientConfigDefault.toBuilder(clientCfg).build();

        assertThat(serverConfigBeanManual, equalTo(serverConfigBean));
        assertThat(clientConfigBeanManual, equalTo(clientConfigBean));
    }

}
