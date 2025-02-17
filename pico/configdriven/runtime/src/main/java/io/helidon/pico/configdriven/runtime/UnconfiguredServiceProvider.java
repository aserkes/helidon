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
import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.AttributeVisitor;
import io.helidon.builder.config.spi.GeneratedConfigBeanBuilderBase;
import io.helidon.common.config.Config;
import io.helidon.pico.api.ContextualServiceQuery;
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.Phase;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.ServiceInfo;
import io.helidon.pico.api.ServiceProviderBindable;
import io.helidon.pico.runtime.PicoInjectionPlan;

/**
 * Used by root service providers when there are no services that have been configured.
 *
 * @param <T>   the service type
 * @param <CB>  the config bean type
 */
class UnconfiguredServiceProvider<T, CB> extends AbstractConfiguredServiceProvider<T, CB> {
    private final AbstractConfiguredServiceProvider<T, CB> delegate;

    /**
     * Default Constructor.
     *
     * @param delegate the root delegate
     */
    UnconfiguredServiceProvider(AbstractConfiguredServiceProvider<T, CB> delegate) {
        assert (delegate != null && delegate.isRootProvider());
        this.delegate = Objects.requireNonNull(delegate);
        rootProvider(delegate);
        assert (rootProvider().orElseThrow() == delegate);
    }

    @Override
    public <C extends io.helidon.common.config.Config, T> Optional<T> toConfigBean(C cfg,
                                                                                   Class<T> configBeanType) {
        return Optional.empty();
    }

    @Override
    protected Optional<T> maybeActivate(ContextualServiceQuery query) {
        return Optional.empty();
    }

    @Override
    public ServiceInfo serviceInfo() {
        return delegate.serviceInfo();
    }

    @Override
    public Phase currentActivationPhase() {
        return delegate.currentActivationPhase();
    }

    @Override
    public DependenciesInfo dependencies() {
        return delegate.dependencies();
    }

    @Override
    public PicoServices picoServices() {
        return delegate.picoServices();
    }

    @Override
    protected String identitySuffix() {
        return delegate.identitySuffix();
    }

    @Override
    public String name(boolean simple) {
        return delegate.name(simple);
    }

    @Override
    public Optional<ServiceProviderBindable<T>> serviceProviderBindable() {
        return delegate.serviceProviderBindable();
    }

    @Override
    public boolean isCustom() {
        return delegate.isCustom();
    }

    @Override
    public boolean isRootProvider() {
        return false;
    }

    @Override
    public Optional<T> first(ContextualServiceQuery query) {
        // the entire point of this class is to really ensure that we do not resolve injection points!
        return Optional.empty();
    }

    @Override
    public Optional<io.helidon.common.config.Config> rawConfig() {
        return delegate.rawConfig();
    }

    @Override
    public Class<?> serviceType() {
        return delegate.serviceType();
    }

    @Override
    public Map<String, PicoInjectionPlan> getOrCreateInjectionPlan(boolean resolveIps) {
        return super.getOrCreateInjectionPlan(resolveIps);
    }

    @Override
    public CB toConfigBean(io.helidon.common.config.Config cfg) {
        return delegate.toConfigBean(cfg);
    }

    @Override
    public GeneratedConfigBeanBuilderBase toConfigBeanBuilder(Config config) {
        return delegate.toConfigBeanBuilder(config);
    }

    @Override
    public <R> void visitAttributes(CB configBean,
                                    AttributeVisitor<Object> visitor,
                                    R userDefinedContext) {
        delegate.visitAttributes(configBean, visitor, userDefinedContext);
    }

    @Override
    public String toConfigBeanInstanceId(CB configBean) {
        return delegate.toConfigBeanInstanceId(configBean);
    }

    @Override
    public Optional<CB> configBean() {
        return Optional.empty();
    }

    @Override
    public void configBeanInstanceId(CB configBean,
                                     String val) {
        delegate.configBeanInstanceId(configBean, val);
    }

    @Override
    protected AbstractConfiguredServiceProvider<T, CB> createInstance(Object configBean) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean drivesActivation() {
        return delegate.drivesActivation();
    }

    @Override
    protected void doPreDestroying(LogEntryAndResult logEntryAndResult) {
        delegate.doPreDestroying(logEntryAndResult);
    }

    @Override
    protected void doDestroying(LogEntryAndResult logEntryAndResult) {
        delegate.doDestroying(logEntryAndResult);
    }

    @Override
    protected void onFinalShutdown() {
        delegate.onFinalShutdown();
    }

}
