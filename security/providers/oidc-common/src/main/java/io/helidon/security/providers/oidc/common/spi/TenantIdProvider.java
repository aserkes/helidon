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

package io.helidon.security.providers.oidc.common.spi;

import io.helidon.config.Config;

/**
 * Java {@link java.util.ServiceLoader} service interface for multitenancy support.
 */
public interface TenantIdProvider {
    /**
     * Create a tenant ID finder API from Helidon config. This method is only called once.
     *
     * @param config configuration (may be empty)
     * @return a tenant id finder API
     */
    TenantIdFinder createTenantIdFinder(Config config);
}
