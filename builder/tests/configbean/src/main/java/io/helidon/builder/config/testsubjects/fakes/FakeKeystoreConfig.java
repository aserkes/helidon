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

package io.helidon.builder.config.testsubjects.fakes;

import java.util.List;

import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.builder.Singular;
import io.helidon.builder.config.ConfigBean;

/**
 * aka KeyConfig.Keystore.Builder
 *
 * This is a ConfigBean since it marries up to the backing config.
 */
@ConfigBean
public interface FakeKeystoreConfig {

    String DEFAULT_KEYSTORE_TYPE = "PKCS12";

    @ConfiguredOption(key = "trust-store")
    boolean trustStore();

    @ConfiguredOption(key = "type", value = DEFAULT_KEYSTORE_TYPE)
    String keystoreType();

    @ConfiguredOption(key = "passphrase")
    char[] keystorePassphrase();

    @ConfiguredOption(key = "key.alias", value = "1")
    String keyAlias();

    @ConfiguredOption(key = "key.passphrase")
    char[] keyPassphrase();

    @ConfiguredOption(key = "cert.alias")
    @Singular("certAlias")
    List<String> certAliases();

    @ConfiguredOption(key = "cert-chain.alias")
    String certChainAlias();

}
