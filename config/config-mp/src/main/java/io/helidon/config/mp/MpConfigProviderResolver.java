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

package io.helidon.config.mp;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.config.ConfigValue;
import io.helidon.config.MetaConfig;
import io.helidon.config.spi.ConfigMapper;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Integration with microprofile config.
 * This class is an implementation of a java service obtained through ServiceLoader.
 */
public class MpConfigProviderResolver extends ConfigProviderResolver {
    private static final System.Logger LOGGER = System.getLogger(MpConfigProviderResolver.class.getName());
    private static final Map<ClassLoader, ConfigDelegate> CONFIGS = new IdentityHashMap<>();
    private static final ReadWriteLock RW_LOCK = new ReentrantReadWriteLock();
    // specific for native image - we want to replace config provided during build with runtime configuration
    private static final List<ConfigDelegate> BUILD_CONFIG = new LinkedList<>();

    @Override
    public Config getConfig() {
        return getConfig(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Config getConfig(ClassLoader classLoader) {
        ClassLoader loader;
        if (classLoader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        } else {
            loader = classLoader;
        }
        Lock lock = RW_LOCK.readLock();
        try {
            lock.lock();
            Config config = CONFIGS.get(loader);

            if (null == config) {
                lock.unlock();
                lock = RW_LOCK.writeLock();
                lock.lock();
                Config c = buildConfig(loader);
                return doRegisterConfig(c, loader);
            } else {
                return config;
            }
        } finally {
            lock.unlock();
        }
    }

    private Config buildConfig(ClassLoader loader) {
        MpConfigBuilder builder = getBuilder();
        builder.forClassLoader(loader);

        // MP Meta Configuration
        Optional<io.helidon.config.Config> meta = MpMetaConfig.metaConfig();

        if (meta.isEmpty()){
            meta = MetaConfig.metaConfig();

            if (meta.isPresent()) {
                builder.metaConfig(meta.get());
                LOGGER.log(Level.WARNING, "You are using Helidon SE meta configuration in a Helidon MP application. Some "
                        + "features work differently, such as environment variable resolving, and mutability");
            }
        } else {
            builder.mpMetaConfig(meta.get());
        }

        if (meta.isEmpty()) {
            // no meta configuration, use defaults
            builder.addDefaultSources();
            builder.addDiscoveredSources();
            builder.addDiscoveredConverters();
        }

        return builder.build();
    }

    @Override
    public MpConfigBuilder getBuilder() {
        return new MpConfigBuilder();
    }

    @Override
    public void registerConfig(Config config, ClassLoader classLoader) {
        ClassLoader usedClassloader;
        if (null == classLoader) {
            usedClassloader = Thread.currentThread().getContextClassLoader();
        } else {
            usedClassloader = classLoader;
        }

        Lock lock = RW_LOCK.writeLock();
        try {
            lock.lock();
            doRegisterConfig(config, usedClassloader);
        } finally {
            lock.unlock();
        }
    }

    /**
     * This method should only be called when running within native image, as soon as runtime configuration
     * is available.
     *
     * @param config configuration to use
     */
    public static void runtimeStart(Config config) {
        if (BUILD_CONFIG.isEmpty()) {
            return;
        }
        BUILD_CONFIG.forEach(it -> it.set(config));
        BUILD_CONFIG.clear();
    }

    /**
     * This method should only be called when generating native image, as late in the process as possible.
     */
    public static void buildTimeEnd() {
        Lock lock = RW_LOCK.writeLock();
        try {
            lock.lock();
            CONFIGS.forEach((key, value) -> BUILD_CONFIG.add(value));
            CONFIGS.clear();
        } finally {
            lock.unlock();
        }
    }

    private ConfigDelegate doRegisterConfig(Config config, ClassLoader classLoader) {
        ConfigDelegate currentConfig = CONFIGS.remove(classLoader);

        if (config instanceof ConfigDelegate) {
            config = ((ConfigDelegate) config).delegate();
        }

        if (null != currentConfig) {
            currentConfig.set(config);
        }

        ConfigDelegate newConfig = new ConfigDelegate(config);
        CONFIGS.put(classLoader, newConfig);

        return newConfig;
    }

    @Override
    public void releaseConfig(Config config) {
        // first attempt to find it
        Lock lock = RW_LOCK.readLock();
        AtomicReference<ClassLoader> cl = new AtomicReference<>();

        // in case we get our own delegate, we want to remove the exact same instance of delegate
        try {
            lock.lock();
            if (config instanceof ConfigDelegate) {
                for (Map.Entry<ClassLoader, ConfigDelegate> entry : CONFIGS.entrySet()) {
                    if (config == entry.getValue()) {
                        cl.set(entry.getKey());
                        break;
                    }
                }
            } else {
                for (Map.Entry<ClassLoader, ConfigDelegate> entry : CONFIGS.entrySet()) {
                    Config configFromRef = entry.getValue().delegate();
                    if (config == configFromRef) {
                        cl.set(entry.getKey());
                        break;
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        // if found, remove it
        if (cl.get() != null) {
            lock = RW_LOCK.writeLock();
            try {
                lock.lock();
                CONFIGS.remove(cl.get());
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * A delegate used to allow replacing configuration at runtime for components
     * that hold a reference to configuration obtained at build time.
     */
    static final class ConfigDelegate implements io.helidon.config.Config, Config {
        private final AtomicReference<Config> delegate = new AtomicReference<>();
        private final AtomicReference<io.helidon.config.Config> helidonDelegate = new AtomicReference<>();

        private ConfigDelegate(Config delegate) {
            set(delegate);
        }

        void set(Config delegate) {
            this.delegate.set(delegate);
            if (delegate instanceof io.helidon.config.Config) {
                this.helidonDelegate.set((io.helidon.config.Config) delegate);
            } else {
                this.helidonDelegate.set(MpConfig.toHelidonConfig(delegate));
            }
        }

        @Override
        public org.eclipse.microprofile.config.ConfigValue getConfigValue(String s) {
            return delegate.get().getConfigValue(s);
        }

        @Override
        public <T> Optional<Converter<T>> getConverter(Class<T> aClass) {
            return delegate.get().getConverter(aClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T unwrap(Class<T> aClass) {
            if (Config.class.equals(aClass)) {
                return (T) delegate.get();
            }
            return delegate.get().unwrap(aClass);
        }

        private io.helidon.config.Config getCurrent() {
            return helidonDelegate.get().context().last();
        }

        @Override
        public Instant timestamp() {
            return getCurrent().timestamp();
        }

        @Override
        public Key key() {
            return getCurrent().key();
        }

        @Override
        public io.helidon.config.Config get(Key key) {
            return getCurrent().get(key);
        }

        @Override
        public io.helidon.config.Config detach() {
            return getCurrent().detach();
        }

        @Override
        public Type type() {
            return getCurrent().type();
        }

        @Override
        public boolean hasValue() {
            return getCurrent().hasValue();
        }

        @Override
        public Stream<io.helidon.config.Config> traverse(Predicate<io.helidon.config.Config> predicate) {
            return getCurrent().traverse();
        }

        @Override
        public <T> T convert(Class<T> type, String value) {
            return getCurrent().convert(type, value);
        }

        @Override
        public <T> ConfigValue<T> as(GenericType<T> genericType) {
            return getCurrent().as(genericType);
        }

        @Override
        public <T> ConfigValue<T> as(Class<T> type) {
            return getCurrent().as(type);
        }

        @Override
        public <T> ConfigValue<T> as(Function<io.helidon.config.Config, T> mapper) {
            return getCurrent().as(mapper);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> type) {
            return getCurrent().asList(type);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Function<io.helidon.config.Config, T> mapper) {
            return getCurrent().asList(mapper);
        }

        @Override
        public ConfigValue<List<io.helidon.config.Config>> asNodeList() {
            return getCurrent().asNodeList();
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() {
            return getCurrent().asMap();
        }

        @Override
        public ConfigMapper mapper() {
            return getCurrent().mapper();
        }

        @Override
        public <T> T getValue(String propertyName, Class<T> propertyType) {
            return delegate.get().getValue(propertyName, propertyType);
        }

        @Override
        public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
            return delegate.get().getOptionalValue(propertyName, propertyType);
        }

        @Override
        public Iterable<String> getPropertyNames() {
            return delegate.get().getPropertyNames();
        }

        @Override
        public Iterable<ConfigSource> getConfigSources() {
            return delegate.get().getConfigSources();
        }

        /**
         * Get the underlying instance of this delegate pattern.
         *
         * @return the instance backing this config delegate
         */
        public Config delegate() {
            return delegate.get();
        }
    }
}
