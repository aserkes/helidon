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

package io.helidon.nima.webclient;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.nima.webclient.spi.DnsResolver;
import io.helidon.nima.webclient.spi.DnsResolverProvider;

/**
 * Provider of the {@link DefaultDnsResolver} instance.
 */
@Weight(Weighted.DEFAULT_WEIGHT)
public class DefaultDnsResolverProvider implements DnsResolverProvider {
    /**
     * Public constructor is required for service loader, do not use directly.
     */
    @Deprecated
    public DefaultDnsResolverProvider() {
    }

    @Override
    public String resolverName() {
        return "default";
    }

    @Override
    public DnsResolver createDnsResolver() {
        return new DefaultDnsResolver();
    }
}
