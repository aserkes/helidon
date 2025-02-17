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

package io.helidon.nima.http.media.spi;

import io.helidon.common.config.Config;
import io.helidon.nima.http.media.MediaSupport;

/**
 * {@link java.util.ServiceLoader} service provider for media supports.
 */
public interface MediaSupportProvider {
    /**
     * Configuration key of this media support provider.
     *
     * @return config key to be used when getting config node to use with {@link #create(io.helidon.common.config.Config)}
     */
    String configKey();

    /**
     * Create media support based on the provided configuration.
     *
     * @param config configuration of the media support
     * @return a new media support to provide readers and writers
     */
    MediaSupport create(Config config);
}
