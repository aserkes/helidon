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

package io.helidon.pico.configdriven.runtime;

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.AttributeVisitor;
import io.helidon.builder.config.spi.ConfigBeanMapper;
import io.helidon.builder.config.spi.GeneratedConfigBeanBuilderBase;
import io.helidon.builder.config.spi.MetaConfigBeanInfo;
import io.helidon.pico.api.ServiceProvider;

/**
 * An extension to {@link io.helidon.pico.api.ServiceProvider} that represents a config-driven service.
 *
 * @param <T>  the type of this service provider manages
 * @param <CB> the type of config beans that this service is configured by
 */
public interface ConfiguredServiceProvider<T, CB> extends ServiceProvider<T>, ConfigBeanMapper {

    /**
     * The type of the service being managed.
     *
     * @return the service type being managed
     */
    Class<?> serviceType();

    /**
     * The {@link io.helidon.builder.config.ConfigBean} type that is used to configure this provider.
     *
     * @return the {@code ConfigBean} type that is used to configure this provider
     */
    Class<?> configBeanType();

    /**
     * The meta config bean information associated with this service provider's {@link io.helidon.builder.config.ConfigBean}.
     *
     * @return the {@code MetaConfigBeanInfo} for this config bean
     */
    MetaConfigBeanInfo metaConfigBeanInfo();

    /**
     * The config bean attributes for our {@link io.helidon.builder.config.ConfigBean}.
     * Generally this method is for internal use only. Most should use {@link #visitAttributes} instead of this method.
     *
     * @return the config bean attributes
     */
    Map<String, Map<String, Object>> configBeanAttributes();

    /**
     * Builds a config bean instance using the configuration.
     *
     * @param config the backing configuration
     * @return the generated config bean instance
     */
    CB toConfigBean(io.helidon.common.config.Config config);

    /**
     * Similar to {@link #toConfigBean(io.helidon.common.config.Config)}, but instead this method builds a config bean builder
     * instance using the configuration.
     *
     * @param config the backing configuration
     * @return the generated config bean instance
     */
    GeneratedConfigBeanBuilderBase toConfigBeanBuilder(io.helidon.common.config.Config config);

    /**
     * Visit the attributes of the config bean, calling the visitor for each attribute in the hierarchy.
     *
     * @param configBean         the config bean to visit
     * @param visitor            the attribute visitor
     * @param userDefinedContext the optional user define context
     * @param <R>                the type of the user defined context
     */
    <R> void visitAttributes(CB configBean,
                             AttributeVisitor<Object> visitor,
                             R userDefinedContext);

    /**
     * Gets the internal config bean instance id for the provided config bean.
     *
     * @param configBean the config bean
     * @return the config bean instance id
     */
    String toConfigBeanInstanceId(CB configBean);

    /**
     * Returns the config bean associated with this managed service provider.
     *
     * @return the config bean associated with this managed service provider
     */
    Optional<CB> configBean();

}
