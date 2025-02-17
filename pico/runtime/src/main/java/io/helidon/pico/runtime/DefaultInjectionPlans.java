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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.pico.api.ContextualServiceQuery;
import io.helidon.pico.api.ContextualServiceQueryDefault;
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.DependencyInfo;
import io.helidon.pico.api.InjectionException;
import io.helidon.pico.api.InjectionPointInfo;
import io.helidon.pico.api.Interceptor;
import io.helidon.pico.api.PicoServiceProviderException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.ServiceInfo;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.ServiceProviderBindable;
import io.helidon.pico.api.ServiceProviderProvider;
import io.helidon.pico.api.Services;
import io.helidon.pico.spi.InjectionResolver;

class DefaultInjectionPlans {

    private DefaultInjectionPlans() {
    }

    /**
     * Converts the inputs to an injection plans for the given service provider.
     *
     * @param picoServices pico services
     * @param self         the reference to the service provider associated with this plan
     * @param dependencies the dependencies
     * @param resolveIps   flag indicating whether injection points should be resolved
     * @param logger            the logger to use for any logging
     * @return the injection plan per element identity belonging to the service provider
     */
    static Map<String, PicoInjectionPlan> createInjectionPlans(PicoServices picoServices,
                                                               ServiceProvider<?> self,
                                                               DependenciesInfo dependencies,
                                                               boolean resolveIps,
                                                               System.Logger logger) {
        Map<String, PicoInjectionPlan> result = new LinkedHashMap<>();
        if (dependencies.allDependencies().isEmpty()) {
            return result;
        }

        dependencies.allDependencies()
                .forEach(dep -> {
                    try {
                        accumulate(dep, result, picoServices, self, resolveIps, logger);
                    } catch (Exception e) {
                        throw new PicoServiceProviderException("An error occurred creating the injection plan", e, self);
                    }
                });

        return result;
    }

    @SuppressWarnings("unchecked")
    private static void accumulate(DependencyInfo dep,
                                   Map<String, PicoInjectionPlan> result,
                                   PicoServices picoServices,
                                   ServiceProvider<?> self,
                                   boolean resolveIps,
                                   System.Logger logger) {
        ServiceInfo selfInfo = self.serviceInfo();
        ServiceInfoCriteria depTo = toCriteria(dep, self, selfInfo);
        Services services = picoServices.services();
        PicoServicesConfig cfg = picoServices.config();
        boolean isPrivateSupported = cfg.supportsJsr330Privates();
        boolean isStaticSupported = cfg.supportsJsr330Statics();

        if (self instanceof InjectionResolver) {
            dep.injectionPointDependencies()
                    .stream()
                    .filter(ipInfo -> (isPrivateSupported || ipInfo.access() != InjectionPointInfo.Access.PRIVATE)
                            && (isStaticSupported || !ipInfo.staticDeclaration()))
                    .forEach(ipInfo -> {
                        String id = ipInfo.id();
                        if (!result.containsKey(id)) {
                            Object resolved = ((InjectionResolver) self)
                                    .resolve(ipInfo, picoServices, self, resolveIps)
                                    .orElse(null);
                            Object target = (resolved instanceof Optional)
                                    ? ((Optional<?>) resolved).orElse(null) : resolved;
                            if (target != null) {
                                PicoInjectionPlanDefault.Builder planBuilder = PicoInjectionPlanDefault.builder()
                                        .serviceProvider(self)
                                        .injectionPointInfo(ipInfo)
                                        .injectionPointQualifiedServiceProviders(toIpQualified(target))
                                        .unqualifiedProviders(toIpUnqualified(target))
                                        .wasResolved(resolved != null);

                                if (ipInfo.optionalWrapped()) {
                                    planBuilder.resolved((target instanceof Optional && ((Optional<?>) target).isEmpty())
                                                                 ? Optional.empty() : Optional.of(target));
                                } else {
                                    if (target instanceof Optional) {
                                        target = ((Optional<Object>) target).orElse(null);
                                    }
                                    planBuilder.resolved(target);
                                }

                                PicoInjectionPlan plan = planBuilder.build();
                                Object prev = result.put(id, plan);
                                assert (prev == null) : ipInfo;
                            }
                        }
                    });
        }

        List<ServiceProvider<?>> tmpServiceProviders = services.lookupAll(depTo, false);
        if (tmpServiceProviders == null || tmpServiceProviders.isEmpty()) {
            if (VoidServiceProvider.INSTANCE.serviceInfo().matches(depTo)) {
                tmpServiceProviders = VoidServiceProvider.LIST_INSTANCE;
            }
        }

        // filter down the selections to not include self
        List<ServiceProvider<?>> serviceProviders =
                (tmpServiceProviders != null && !tmpServiceProviders.isEmpty())
                        ? tmpServiceProviders.stream()
                                .filter(sp -> !isSelf(self, sp))
                                .collect(Collectors.toList())
                        : tmpServiceProviders;

        dep.injectionPointDependencies()
                .stream()
                .filter(ipInfo ->
                                (isPrivateSupported || ipInfo.access() != InjectionPointInfo.Access.PRIVATE)
                                        && (isStaticSupported || !ipInfo.staticDeclaration()))
                .forEach(ipInfo -> {
                    String id = ipInfo.id();
                    if (!result.containsKey(id)) {
                        Object resolved = (resolveIps)
                                ? resolve(self, ipInfo, serviceProviders, logger) : null;
                        if (!resolveIps && !ipInfo.optionalWrapped()
                                && (serviceProviders == null || serviceProviders.isEmpty())
                                && !allowNullableInjectionPoint(ipInfo)) {
                            throw DefaultServices.resolutionBasedInjectionError(
                                    ipInfo.dependencyToServiceInfo());
                        }
                        PicoInjectionPlan plan = PicoInjectionPlanDefault.builder()
                                .injectionPointInfo(ipInfo)
                                .injectionPointQualifiedServiceProviders(serviceProviders)
                                .serviceProvider(self)
                                .wasResolved(resolveIps)
                                .resolved((resolved instanceof Optional<?> && ((Optional<?>) resolved).isEmpty())
                                                ? Optional.empty() : Optional.ofNullable(resolved))
                                .build();
                        Object prev = result.put(id, plan);
                        assert (prev == null) : ipInfo;
                    }
                });
    }

    /**
     * Creates and maybe adjusts the criteria to match the context of who is doing the lookup.
     *
     * @param dep       the dependency info to lookup
     * @param self      the service doing the lookup
     * @param selfInfo  the service info for the service doing the lookup
     * @return the criteria
     */
    static ServiceInfoCriteria toCriteria(DependencyInfo dep,
                                          ServiceProvider<?> self,
                                          ServiceInfo selfInfo) {
        ServiceInfoCriteria criteria = dep.dependencyTo();
        ServiceInfoCriteriaDefault.Builder builder = null;
        if (selfInfo.declaredWeight().isPresent()
                && selfInfo.contractsImplemented().containsAll(criteria.contractsImplemented())) {
            // if we have a weight on ourselves, and we inject an interface that we actually offer, then
            // be sure to use it to get lower weighted injection points
            builder = ServiceInfoCriteriaDefault.toBuilder(criteria)
                    .weight(selfInfo.declaredWeight().get());
        }

        if ((self instanceof ServiceProviderBindable) && ((ServiceProviderBindable<?>) self).isInterceptor()) {
            if (builder == null) {
                builder = ServiceInfoCriteriaDefault.toBuilder(criteria);
            }
            builder = builder.includeIntercepted(true);
        }

        return (builder != null) ? builder.build() : criteria;
    }

    /**
     * Resolution comes after the plan was loaded or created.
     *
     * @param self              the reference to the service provider associated with this plan
     * @param ipInfo            the injection point
     * @param serviceProviders  the service providers that qualify
     * @param logger            the logger to use for any logging
     * @return the resolution (and activation) of the qualified service provider(s) in the form acceptable to the injection point
     */
    @SuppressWarnings("unchecked")
    static Object resolve(ServiceProvider<?> self,
                          InjectionPointInfo ipInfo,
                          List<ServiceProvider<?>> serviceProviders,
                          System.Logger logger) {
        if (ipInfo.staticDeclaration()) {
            throw new InjectionException(ipInfo + ": static is not supported", null, self);
        }
        if (ipInfo.access() == InjectionPointInfo.Access.PRIVATE) {
            throw new InjectionException(ipInfo + ": private is not supported", null, self);
        }

        try {
            if (Void.class.getName().equals(ipInfo.serviceTypeName())) {
                return null;
            }

            if (ipInfo.listWrapped()) {
                if (ipInfo.optionalWrapped()) {
                    throw new InjectionException("Optional + List injection is not supported for "
                                                         + ipInfo.serviceTypeName() + "." + ipInfo.elementName());
                }

                if (serviceProviders.isEmpty()) {
                    if (!allowNullableInjectionPoint(ipInfo)) {
                        throw new InjectionException("Expected to resolve a service appropriate for "
                                                             + ipInfo.serviceTypeName() + "." + ipInfo.elementName(),
                                                     DefaultServices
                                                             .resolutionBasedInjectionError(
                                                                     ipInfo.dependencyToServiceInfo()),
                                                     self);
                    } else {
                        return serviceProviders;
                    }
                }

                if (ipInfo.providerWrapped() && !ipInfo.optionalWrapped()) {
                    return serviceProviders;
                }

                if (ipInfo.listWrapped() && !ipInfo.optionalWrapped()) {
                    return toEligibleInjectionRefs(ipInfo, self, serviceProviders, true);
                }
            } else if (serviceProviders.isEmpty()) {
                if (ipInfo.optionalWrapped()) {
                    return Optional.empty();
                } else {
                    throw new InjectionException("Expected to resolve a service appropriate for "
                                                         + ipInfo.serviceTypeName() + "." + ipInfo.elementName(),
                                                 DefaultServices.resolutionBasedInjectionError(ipInfo.dependencyToServiceInfo()),
                                                 self);
                }
            } else {
                // "standard" case
                ServiceProvider<?> serviceProvider = serviceProviders.get(0);
                Optional<ServiceProviderBindable<?>> serviceProviderBindable =
                        DefaultServiceBinder.toBindableProvider(DefaultServiceBinder.toRootProvider(serviceProvider));
                if (serviceProviderBindable.isPresent()
                        && serviceProviderBindable.get() != serviceProvider
                        && serviceProviderBindable.get() instanceof ServiceProviderProvider) {
                    serviceProvider = serviceProviderBindable.get();
                    serviceProviders = (List<ServiceProvider<?>>) ((ServiceProviderProvider) serviceProvider)
                            .serviceProviders(ipInfo.dependencyToServiceInfo(), true, false);
                    if (!serviceProviders.isEmpty()) {
                        serviceProvider = serviceProviders.get(0);
                    }
                }

                if (ipInfo.providerWrapped()) {
                    return ipInfo.optionalWrapped() ? Optional.of(serviceProvider) : serviceProvider;
                }

                if (ipInfo.optionalWrapped()) {
                    Optional<?> optVal;
                    try {
                        optVal = Objects.requireNonNull(
                                serviceProvider.first(ContextualServiceQuery.create(ipInfo, false)));
                    } catch (InjectionException e) {
                        logger.log(System.Logger.Level.WARNING, e.getMessage(), e);
                        optVal = Optional.empty();
                    }
                    return optVal;
                }

                ContextualServiceQuery query = ContextualServiceQuery.create(ipInfo, true);
                Optional<?> first = serviceProvider.first(query);
                return first.orElse(null);
            }
        } catch (InjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw expectedToResolveCriteria(ipInfo, t, self);
        }

        throw expectedToResolveCriteria(ipInfo, null, self);
    }

    private static List<ServiceProvider<?>> toIpQualified(Object target) {
        if (target instanceof Collection) {
            List<ServiceProvider<?>> result = new ArrayList<>();
            ((Collection<?>) target).stream()
                    .map(DefaultInjectionPlans::toIpQualified)
                    .forEach(result::addAll);
            return result;
        }

        return (target instanceof AbstractServiceProvider)
                ? List.of((ServiceProvider<?>) target)
                : List.of();
    }

    private static List<?> toIpUnqualified(Object target) {
        if (target instanceof Collection) {
            List<Object> result = new ArrayList<>();
            ((Collection<?>) target).stream()
                    .map(DefaultInjectionPlans::toIpUnqualified)
                    .forEach(result::addAll);
            return result;
        }

        return (target == null || target instanceof AbstractServiceProvider)
                ? List.of()
                : List.of(target);
    }

    private static boolean isSelf(ServiceProvider<?> self,
                                  Object other) {
        assert (self != null);

        if (self == other) {
            return true;
        }

        if (self instanceof ServiceProviderBindable) {
            Object selfInterceptor = ((ServiceProviderBindable<?>) self).interceptor().orElse(null);

            if (other == selfInterceptor) {
                return true;
            }
        }

        return false;
    }

    private static boolean allowNullableInjectionPoint(InjectionPointInfo ipInfo) {
        ServiceInfoCriteria missingServiceInfo = ipInfo.dependencyToServiceInfo();
        Set<String> contractsNeeded = missingServiceInfo.contractsImplemented();
        return (1 == contractsNeeded.size() && contractsNeeded.contains(Interceptor.class.getName()));
    }

    @SuppressWarnings({"unchecked", "rawTypes"})
    private static List<?> toEligibleInjectionRefs(InjectionPointInfo ipInfo,
                                                   ServiceProvider<?> self,
                                                   List<ServiceProvider<?>> list,
                                                   boolean expected) {
        List<?> result = new ArrayList<>();

        ContextualServiceQuery query = ContextualServiceQueryDefault.builder()
                .injectionPointInfo(ipInfo)
                .serviceInfoCriteria(ipInfo.dependencyToServiceInfo())
                .expected(expected);
        for (ServiceProvider<?> sp : list) {
            Collection instances = sp.list(query);
            result.addAll(instances);
        }

        if (expected && result.isEmpty()) {
            throw expectedToResolveCriteria(ipInfo, null, self);
        }

        return result;
    }

    private static InjectionException expectedToResolveCriteria(InjectionPointInfo ipInfo,
                                                                Throwable cause,
                                                                ServiceProvider<?> self) {
        String msg = (cause == null) ? "expected" : "failed";
        return new InjectionException(msg + " to resolve a service instance appropriate for '"
                                              + ipInfo.serviceTypeName() + "." + ipInfo.elementName()
                                              + "' with criteria = '" + ipInfo.dependencyToServiceInfo(),
                                      cause, self);
    }

}
