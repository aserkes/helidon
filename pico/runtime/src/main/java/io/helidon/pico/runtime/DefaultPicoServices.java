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

package io.helidon.pico.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.pico.api.ActivationLog;
import io.helidon.pico.api.ActivationLogEntry;
import io.helidon.pico.api.ActivationLogEntryDefault;
import io.helidon.pico.api.ActivationLogQuery;
import io.helidon.pico.api.ActivationPhaseReceiver;
import io.helidon.pico.api.ActivationResult;
import io.helidon.pico.api.ActivationResultDefault;
import io.helidon.pico.api.ActivationStatus;
import io.helidon.pico.api.Application;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.CallingContext;
import io.helidon.pico.api.CallingContextFactory;
import io.helidon.pico.api.Event;
import io.helidon.pico.api.Injector;
import io.helidon.pico.api.InjectorOptions;
import io.helidon.pico.api.InjectorOptionsDefault;
import io.helidon.pico.api.Metrics;
import io.helidon.pico.api.MetricsDefault;
import io.helidon.pico.api.ModuleComponent;
import io.helidon.pico.api.Phase;
import io.helidon.pico.api.PicoException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.Resettable;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;
import io.helidon.pico.api.ServiceProvider;

import static io.helidon.pico.api.CallingContext.toErrorMessage;

/**
 * The default implementation for {@link PicoServices}.
 */
class DefaultPicoServices implements PicoServices, Resettable {
    static final System.Logger LOGGER = System.getLogger(DefaultPicoServices.class.getName());

    private final AtomicBoolean initializingServicesStarted = new AtomicBoolean(false);
    private final AtomicBoolean initializingServicesFinished = new AtomicBoolean(false);
    private final AtomicBoolean isBinding = new AtomicBoolean(false);
    private final AtomicReference<DefaultServices> services = new AtomicReference<>();
    private final AtomicReference<List<ModuleComponent>> moduleList = new AtomicReference<>();
    private final AtomicReference<List<Application>> applicationList = new AtomicReference<>();
    private final Bootstrap bootstrap;
    private final PicoServicesConfig cfg;
    private final boolean isGlobal;
    private final DefaultActivationLog log;
    private final State state = State.create(Phase.INIT);
    private CallingContext initializationCallingContext;

    /**
     * Constructor taking the bootstrap.
     *
     * @param bootstrap the bootstrap configuration
     * @param global    flag indicating whether this is the global con
     */
    DefaultPicoServices(Bootstrap bootstrap,
                        boolean global) {
        this.bootstrap = bootstrap;
        this.cfg = DefaultPicoServicesConfig.createDefaultConfigBuilder().build();
        this.isGlobal = global;
        this.log = cfg.activationLogs()
                ? DefaultActivationLog.createRetainedLog(LOGGER)
                : DefaultActivationLog.createUnretainedLog(LOGGER);
    }

    @Override
    public Bootstrap bootstrap() {
        return bootstrap;
    }

    @Override
    public PicoServicesConfig config() {
        return cfg;
    }

    @Override
    public Optional<ActivationLog> activationLog() {
        return Optional.of(log);
    }

    @Override
    public Optional<Injector> injector() {
        return Optional.of(new DefaultInjector());
    }

    @Override
    public Optional<Metrics> metrics() {
        DefaultServices thisServices = services.get();
        if (thisServices == null) {
            // never has been any lookup yet
            return Optional.of(MetricsDefault.builder().build());
        }
        return Optional.of(thisServices.metrics());
    }

    @Override
    public Optional<Set<ServiceInfoCriteria>> lookups() {
        if (!cfg.serviceLookupCaching()) {
            return Optional.empty();
        }

        DefaultServices thisServices = services.get();
        if (thisServices == null) {
            // never has been any lookup yet
            return Optional.of(Set.of());
        }
        return Optional.of(thisServices.cache().keySet());
    }

    @Override
    public Optional<DefaultServices> services(boolean initialize) {
        if (!initialize) {
            return Optional.ofNullable(services.get());
        }

        if (!initializingServicesStarted.getAndSet(true)) {
            try {
                initializeServices();
            } catch (Throwable t) {
                state.lastError(t);
                initializingServicesStarted.set(false);
                if (t instanceof PicoException) {
                    throw (PicoException) t;
                } else {
                    throw new PicoException("Failed to initialize: " + t.getMessage(), t);
                }
            } finally {
                state.finished(true);
                initializingServicesFinished.set(true);
            }
        }

        DefaultServices thisServices = services.get();
        if (thisServices == null) {
            throw new PicoException("Must reset() after shutdown()");
        }
        return Optional.of(thisServices);
    }

    @Override
    public Optional<Map<String, ActivationResult>> shutdown() {
        Map<String, ActivationResult> result = Map.of();
        DefaultServices current = services.get();
        if (services.compareAndSet(current, null) && current != null) {
            State currentState = state.clone().currentPhase(Phase.PRE_DESTROYING);
            log("started shutdown");
            result = doShutdown(current, currentState);
            log("finished shutdown");
        }
        return Optional.ofNullable(result);
    }

    @Override
    // note that this is typically only called during testing, and also in the pico-maven-plugin
    public boolean reset(boolean deep) {
        try {
            assertNotInitializing();
            if (isInitializing() || isInitialized()) {
                // we allow dynamic updates leading up to initialization - after that it should be prevented if not configured on
                DefaultServices.assertPermitsDynamic(cfg);
            }
            boolean result = deep;

            DefaultServices prev = services.get();
            if (prev != null) {
                boolean affected = prev.reset(deep);
                result |= affected;
            }

            boolean affected = log.reset(deep);
            result |= affected;

            if (deep) {
                isBinding.set(false);
                moduleList.set(null);
                applicationList.set(null);
                if (prev != null) {
                    services.set(new DefaultServices(cfg));
                }
                state.reset(true);
                initializingServicesStarted.set(false);
                initializingServicesFinished.set(false);
                initializationCallingContext = null;
            }

            return result;
        } catch (Exception e) {
            throw new PicoException("Failed to reset (state=" + state
                                            + ", isInitialized=" + isInitialized()
                                            + ", isInitializing=" + isInitializing() + ")", e);
        }
    }

    /**
     * Returns true if Pico is in the midst of initialization.
     *
     * @return true if initialization is underway
     */
    public boolean isInitializing() {
        return initializingServicesStarted.get() && !initializingServicesFinished.get();
    }

    /**
     * Returns true if Pico was initialized.
     *
     * @return true if already initialized
     */
    public boolean isInitialized() {
        return initializingServicesStarted.get() && initializingServicesFinished.get();
    }

    private Map<String, ActivationResult> doShutdown(DefaultServices services,
                                                     State state) {
        long start = System.currentTimeMillis();

        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(false);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setName(PicoServicesConfig.NAME + "-shutdown-" + System.currentTimeMillis());
            return thread;
        };

        Shutdown shutdown = new Shutdown(services, state);
        ExecutorService es = Executors.newSingleThreadExecutor(threadFactory);
        long finish;
        try {
            return es.submit(shutdown)
                    // https://github.com/helidon-io/helidon/issues/6434: have an appropriate timeout config for this
//                    .get(cfg.activationDeadlockDetectionTimeoutMillis(), TimeUnit.MILLISECONDS);
                    .get();
        } catch (Throwable t) {
            finish = System.currentTimeMillis();
            errorLog("Error detected during shutdown (elapsed = " + (finish - start) + " ms)", t);
            throw new PicoException("Error detected during shutdown", t);
        } finally {
            es.shutdown();
            state.finished(true);
            finish = System.currentTimeMillis();
            log("Finished shutdown (elapsed = " + (finish - start) + " ms)");
        }
    }

    private void assertNotInitializing() {
        if (isBinding.get() || isInitializing()) {
            CallingContext initializationCallingContext = this.initializationCallingContext;
            String desc = "Calling reset() during the initialization sequence is not supported (binding="
                    + isBinding + ", initializingServicesFinished="
                    + initializingServicesFinished + ")";
            String msg = (initializationCallingContext == null)
                    ? toErrorMessage(desc) : toErrorMessage(initializationCallingContext, desc);
            throw new PicoException(msg);
        }
    }

    private void initializeServices() {
        initializationCallingContext = CallingContextFactory.create(false).orElse(null);

        if (services.get() == null) {
            services.set(new DefaultServices(cfg));
        }

        DefaultServices thisServices = services.get();
        thisServices.state(state);
        state.currentPhase(Phase.ACTIVATION_STARTING);

        if (isGlobal) {
            // iterate over all modules, binding to each one's set of services, but with NO activations
            List<ModuleComponent> modules = findModules(true);
            try {
                isBinding.set(true);
                bindModules(thisServices, modules);
            } finally {
                isBinding.set(false);
            }
        }

        state.currentPhase(Phase.GATHERING_DEPENDENCIES);

        if (isGlobal) {
            // look for the literal injection plan
            // typically only be one Application in non-testing runtimes
            List<Application> apps = findApplications(true);
            bindApplications(thisServices, apps);
        }

        state.currentPhase(Phase.POST_BIND_ALL_MODULES);

        if (isGlobal) {
            // only the global services registry gets eventing (no particular reason though)
            thisServices.allServiceProviders(false).stream()
                    .filter(sp -> sp instanceof ActivationPhaseReceiver)
                    .map(sp -> (ActivationPhaseReceiver) sp)
                    .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.POST_BIND_ALL_MODULES));
        }

        state.currentPhase(Phase.FINAL_RESOLVE);

        if (isGlobal || cfg.supportsCompileTime()) {
            thisServices.allServiceProviders(false).stream()
                    .filter(sp -> sp instanceof ActivationPhaseReceiver)
                    .map(sp -> (ActivationPhaseReceiver) sp)
                    .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.FINAL_RESOLVE));
        }

        state.currentPhase(Phase.SERVICES_READY);

        // notify interested service providers of "readiness"...
        thisServices.allServiceProviders(false).stream()
                .filter(sp -> sp instanceof ActivationPhaseReceiver)
                .map(sp -> (ActivationPhaseReceiver) sp)
                .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.SERVICES_READY));

        state.finished(true);
    }

    private List<Application> findApplications(boolean load) {
        List<Application> result = applicationList.get();
        if (result != null) {
            return result;
        }

        result = new ArrayList<>();
        if (load) {
            ServiceLoader<Application> serviceLoader = ServiceLoader.load(Application.class);
            for (Application app : serviceLoader) {
                result.add(app);
            }

            if (!cfg.permitsDynamic()) {
                applicationList.compareAndSet(null, List.copyOf(result));
                result = applicationList.get();
            }
        }
        return result;
    }

    private List<ModuleComponent> findModules(boolean load) {
        List<ModuleComponent> result = moduleList.get();
        if (result != null) {
            return result;
        }

        result = new ArrayList<>();
        if (load) {
            ServiceLoader<ModuleComponent> serviceLoader = ServiceLoader.load(ModuleComponent.class);
            for (ModuleComponent module : serviceLoader) {
                result.add(module);
            }

            if (!cfg.permitsDynamic()) {
                moduleList.compareAndSet(null, List.copyOf(result));
                result = moduleList.get();
            }
        }
        return result;
    }

    private void bindApplications(DefaultServices services,
                          Collection<Application> apps) {
        if (!cfg.usesCompileTimeApplications()) {
            LOGGER.log(System.Logger.Level.DEBUG, "application binding is disabled");
            return;
        }

        if (apps.size() > 1) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "there is typically only 1 application instance; app instances = " + apps);
        } else if (apps.isEmpty()) {
            LOGGER.log(System.Logger.Level.TRACE, "no " + Application.class.getName() + " was found.");
            return;
        }

        DefaultInjectionPlanBinder injectionPlanBinder = new DefaultInjectionPlanBinder(services);
        apps.forEach(app -> services.bind(this, injectionPlanBinder, app));
    }

    private void bindModules(DefaultServices services,
                             Collection<ModuleComponent> modules) {
        if (!cfg.usesCompileTimeModules()) {
            LOGGER.log(System.Logger.Level.DEBUG, "module binding is disabled");
            return;
        }

        if (modules.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "no " + ModuleComponent.class.getName() + " was found.");
        } else {
            modules.forEach(module -> services.bind(this, module, isBinding.get()));
        }
    }

    private void log(String message) {
        ActivationLogEntry entry = ActivationLogEntryDefault.builder()
                .message(message)
                .build();
        log.record(entry);
    }

    private void errorLog(String message,
                          Throwable t) {
        ActivationLogEntry entry = ActivationLogEntryDefault.builder()
                .message(message)
                .error(t)
                .build();
        log.record(entry);
    }

    /**
     * Will attempt to shut down in reverse order of activation, but only if activation logs are retained.
     */
    private class Shutdown implements Callable<Map<String, ActivationResult>> {
        private final DefaultServices services;
        private final State state;
        private final ActivationLog log;
        private final Injector injector;
        private final InjectorOptions opts = InjectorOptionsDefault.builder().build();
        private final Map<String, ActivationResult> map = new LinkedHashMap<>();

        Shutdown(DefaultServices services,
                 State state) {
            this.services = Objects.requireNonNull(services);
            this.state = Objects.requireNonNull(state);
            this.injector = injector().orElseThrow();
            this.log = activationLog().orElseThrow();
        }

        @Override
        public Map<String, ActivationResult> call() {
            state.currentPhase(Phase.DESTROYED);

            ActivationLogQuery query = log.toQuery().orElse(null);
            if (query != null) {
                // we can lean on the log entries in order to shut down in reverse chronological order
                List<ActivationLogEntry> fullyActivationLog = new ArrayList<>(query.fullActivationLog());
                if (!fullyActivationLog.isEmpty()) {
                    LinkedHashSet<ServiceProvider<?>> serviceProviderActivations = new LinkedHashSet<>();

                    Collections.reverse(fullyActivationLog);
                    fullyActivationLog.stream()
                            .map(ActivationLogEntry::serviceProvider)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .forEach(serviceProviderActivations::add);

                    // prepare for the shutdown log event sequence
                    log.toQuery().ifPresent(it -> it.reset(false));

                    // shutdown using the reverse chronological ordering in the log for starters
                    doFinalShutdown(serviceProviderActivations);
                }
            }

            // next get all services that are beyond INIT state, and sort by runlevel order, and shut those down also
            List<ServiceProvider<?>> serviceProviders = services.lookupAll(ServiceInfoCriteriaDefault.builder().build(), false);
            serviceProviders = serviceProviders.stream()
                    .filter(sp -> sp.currentActivationPhase().eligibleForDeactivation())
                    .collect(Collectors.toList());
            serviceProviders.sort((o1, o2) -> {
                int runLevel1 = o1.serviceInfo().realizedRunLevel();
                int runLevel2 = o2.serviceInfo().realizedRunLevel();
                return Integer.compare(runLevel1, runLevel2);
            });
            doFinalShutdown(serviceProviders);

            // finally, clear everything
            reset(false);

            return map;
        }

        private void doFinalShutdown(Collection<ServiceProvider<?>> serviceProviders) {
            for (ServiceProvider<?> csp : serviceProviders) {
                Phase startingActivationPhase = csp.currentActivationPhase();
                ActivationResult result;
                try {
                    result = injector.deactivate(csp, opts);
                } catch (Throwable t) {
                    errorLog("error during shutdown", t);
                    result = ActivationResultDefault.builder()
                            .serviceProvider(csp)
                            .startingActivationPhase(startingActivationPhase)
                            .targetActivationPhase(Phase.DESTROYED)
                            .finishingActivationPhase(csp.currentActivationPhase())
                            .finishingStatus(ActivationStatus.FAILURE)
                            .error(t)
                            .build();
                }
                map.put(csp.serviceInfo().serviceTypeName(), result);
            }
        }
    }

}
