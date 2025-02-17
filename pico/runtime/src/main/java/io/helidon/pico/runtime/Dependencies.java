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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.DependenciesInfoDefault;
import io.helidon.pico.api.DependencyInfo;
import io.helidon.pico.api.DependencyInfoDefault;
import io.helidon.pico.api.ElementInfo;
import io.helidon.pico.api.InjectionPointInfo;
import io.helidon.pico.api.InjectionPointInfoDefault;
import io.helidon.pico.api.QualifierAndValue;
import io.helidon.pico.api.QualifierAndValueDefault;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;

/**
 * This is the class the code-generator will target that will be used at runtime for a service provider to build up its
 * dependencies expressed as {@link io.helidon.pico.api.DependenciesInfo}.
 */
public class Dependencies {

    private Dependencies() {
    }

    /**
     * Creates a builder.
     *
     * @param serviceTypeName the service type name
     * @return the fluent builder
     */
    public static BuilderContinuation builder(String serviceTypeName) {
        Objects.requireNonNull(serviceTypeName);
        return new BuilderContinuation(serviceTypeName);
    }

    /**
     * Combine the dependency info from the two sources to create a merged set of dependencies.
     *
     * @param parentDeps the parent set of dependencies
     * @param deps       the child set of dependencies
     * @return the combined set
     */
    public static DependenciesInfo combine(DependenciesInfo parentDeps,
                                           DependenciesInfo deps) {
        Objects.requireNonNull(parentDeps);
        Objects.requireNonNull(deps);

        DependenciesInfoDefault.Builder builder = (deps instanceof DependenciesInfoDefault.Builder)
                ? (DependenciesInfoDefault.Builder) deps
                : DependenciesInfoDefault.toBuilder(deps);
        parentDeps.serviceInfoDependencies().forEach(builder::addServiceInfoDependency);
        return forceBuild(builder);
    }

    static String toBaseIdentity(InjectionPointInfo dep) {
        ElementInfo.ElementKind kind = Objects.requireNonNull(dep.elementKind());
        String elemName = Objects.requireNonNull(dep.elementName());
        ElementInfo.Access access = Objects.requireNonNull(dep.access());
        Supplier<String> packageName = toPackageName(dep.serviceTypeName());

        String baseId;
        if (ElementInfo.ElementKind.FIELD == kind) {
            baseId = toFieldIdentity(elemName, packageName);
        } else {
            baseId = toMethodBaseIdentity(elemName,
                                          dep.elementArgs().orElseThrow(),
                                          access, packageName);
        }
        return baseId;
    }

    static String toId(InjectionPointInfo dep) {
        ElementInfo.ElementKind kind = Objects.requireNonNull(dep.elementKind());
        String elemName = Objects.requireNonNull(dep.elementName());
        ElementInfo.Access access = Objects.requireNonNull(dep.access());
        Supplier<String> packageName = toPackageName(dep.serviceTypeName());

        String id;
        if (ElementInfo.ElementKind.FIELD == kind) {
            id = toFieldIdentity(elemName, packageName);
        } else {
            id = toMethodIdentity(elemName,
                                  dep.elementArgs().orElseThrow(),
                                  dep.elementOffset().orElseThrow(() -> new IllegalStateException("Failed on " + elemName)),
                                  access,
                                  packageName);
        }
        return id;
    }

    /**
     * The field's identity and its base identity are the same since there is no arguments to handle.
     *
     * @param elemName    the non-null field name
     * @param packageName the package name of the owning service type containing the field
     * @return the field identity (relative to the owning service type)
     */
    public static String toFieldIdentity(String elemName,
                                         Supplier<String> packageName) {
        String id = Objects.requireNonNull(elemName);
        String pName = (packageName == null) ? null : packageName.get();
        if (pName != null) {
            id = pName + "." + id;
        }
        return id;
    }

    /**
     * Computes the base identity given the method name and the number of arguments to the method.
     *
     * @param elemName       the method name
     * @param methodArgCount the number of arguments to the method
     * @param access         the method's access
     * @param packageName    the method's enclosing package name
     * @return the base identity (relative to the owning service type)
     */
    public static String toMethodBaseIdentity(String elemName,
                                              int methodArgCount,
                                              ElementInfo.Access access,
                                              Supplier<String> packageName) {
        String id = Objects.requireNonNull(elemName) + "|" + methodArgCount;
        if (ElementInfo.Access.PACKAGE_PRIVATE == access || elemName.equals(InjectionPointInfo.CONSTRUCTOR)) {
            String pName = (packageName == null) ? null : packageName.get();
            if (pName != null) {
                id = pName + "." + id;
            }
        }
        return id;
    }

    /**
     * Computes the method's unique identity, taking into consideration the number of args it accepts
     * plus any optionally provided specific argument offset position.
     *
     * @param elemName       the method name
     * @param methodArgCount the number of arguments to the method
     * @param elemOffset     the optional parameter offset
     * @param access         the access for the method
     * @param packageName    the package name of the owning service type containing the method
     * @return the unique identity (relative to the owning service type)
     */
    public static String toMethodIdentity(String elemName,
                                          int methodArgCount,
                                          Integer elemOffset,
                                          ElementInfo.Access access,
                                          Supplier<String> packageName) {
        String result = toMethodBaseIdentity(elemName, methodArgCount, access, packageName);

        if (elemOffset == null) {
            return result;
        }

        assert (elemOffset <= methodArgCount) : result;
        return result + "(" + elemOffset + ")";
    }

    /**
     * Returns the non-builder version of the passed dependencies.
     *
     * @param deps the dependencies, but might be actually in builder form
     * @return will always be the built version of the dependencies
     */
    private static DependenciesInfo forceBuild(DependenciesInfo deps) {
        Objects.requireNonNull(deps);

        if (deps instanceof DependenciesInfoDefault.Builder) {
            deps = ((DependenciesInfoDefault.Builder) deps).build();
        }

        return deps;
    }

    private static Supplier<String> toPackageName(String serviceTypeName) {
        return () -> toPackageName(TypeNameDefault.createFromTypeName(serviceTypeName));
    }

    private static String toPackageName(TypeName typeName) {
        return (typeName != null) ? typeName.packageName() : null;
    }

    /**
     * The continuation builder. This is a specialized builder used within the generated Pico {@link io.helidon.pico.api.Activator}.
     * It is specialized in that it validates and decorates over the normal builder, and provides a more streamlined interface.
     */
    public static class BuilderContinuation {
        private DependenciesInfoDefault.Builder builder;
        private InjectionPointInfoDefault.Builder ipInfoBuilder;

        private BuilderContinuation(String serviceTypeName) {
            this.builder = DependenciesInfoDefault.builder()
                    .fromServiceTypeName(serviceTypeName);
        }

        /**
         * Adds a new dependency item.
         *
         * @param elemName the element name
         * @param elemType the element type
         * @param kind     the element kind
         * @param access   the element access
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation add(String elemName,
                                       Class<?> elemType,
                                       InjectionPointInfo.ElementKind kind,
                                       InjectionPointInfo.Access access) {
            if (InjectionPointInfo.ElementKind.FIELD != kind && Void.class != elemType) {
                throw new IllegalStateException("Should not use this for method element types");
            }
            String fromServiceTypeName = builder.fromServiceTypeName().orElseThrow();
            return add(fromServiceTypeName, elemName, elemType.getName(), kind, 0, access);
        }

        /**
         * Adds a new dependency item.
         *
         * @param elemName the element name
         * @param elemType the element type
         * @param kind     the element kind
         * @param elemArgs for methods, the number of arguments the method takes
         * @param access   the element access
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation add(String elemName,
                                       Class<?> elemType,
                                       InjectionPointInfo.ElementKind kind,
                                       int elemArgs,
                                       InjectionPointInfo.Access access) {
            if (InjectionPointInfo.ElementKind.FIELD == kind && 0 != elemArgs) {
                throw new IllegalStateException("Should not have any arguments for field types: " + elemName);
            }
            String fromServiceTypeName = builder.fromServiceTypeName().orElseThrow();
            return add(fromServiceTypeName, elemName, elemType.getName(), kind, elemArgs, access);
        }

        /**
         * Adds a new dependency item.
         *
         * @param serviceType the service type
         * @param elemName    the element name
         * @param elemType    the element type
         * @param kind        the element kind
         * @param access      the element access
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation add(Class<?> serviceType,
                                       String elemName,
                                       Class<?> elemType,
                                       InjectionPointInfo.ElementKind kind,
                                       InjectionPointInfo.Access access) {
            if (InjectionPointInfo.ElementKind.FIELD != kind) {
                throw new IllegalStateException("Should not use this for method element types");
            }
            return add(serviceType.getName(), elemName, elemType.getName(), kind, 0, access);
        }

        /**
         * Adds a new dependency item.
         *
         * @param serviceType the service type
         * @param elemName    the element name
         * @param elemType    the element type
         * @param kind        the element kind
         * @param elemArgs    used for methods only; the number of arguments the method accepts
         * @param access      the element access
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation add(Class<?> serviceType,
                                       String elemName,
                                       Class<?> elemType,
                                       InjectionPointInfo.ElementKind kind,
                                       int elemArgs,
                                       InjectionPointInfo.Access access) {
            return add(serviceType.getName(), elemName, elemType.getName(), kind, elemArgs, access);
        }

        /**
         * Adds a new dependency item.
         *
         * @param ipInfo the injection point info already built
         * @return the builder
         */
        public BuilderContinuation add(InjectionPointInfo ipInfo) {
            commitLastDependency();

            ipInfoBuilder = InjectionPointInfoDefault.toBuilder(ipInfo);
            return this;
        }

        /**
         * Sets the element offset.
         *
         * @param offset the offset
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation elemOffset(Integer offset) {
            ipInfoBuilder.elementOffset(Optional.ofNullable(offset));
            return this;
        }

        /**
         * Sets the flag indicating the injection point is a list.
         *
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation listWrapped() {
            return listWrapped(true);
        }

        /**
         * Sets the flag indicating the injection point is a list.
         *
         * @param val true if list type
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation listWrapped(boolean val) {
            ipInfoBuilder.listWrapped(val);
            return this;
        }

        /**
         * Sets the flag indicating the injection point is a provider.
         *
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation providerWrapped() {
            return providerWrapped(true);
        }

        /**
         * Sets the flag indicating the injection point is a provider.
         *
         * @param val true if provider type
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation providerWrapped(boolean val) {
            ipInfoBuilder.providerWrapped(val);
            return this;
        }

        /**
         * Sets the flag indicating the injection point is an {@link java.util.Optional} type.
         *
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation optionalWrapped() {
            return optionalWrapped(true);
        }

        /**
         * Sets the flag indicating the injection point is an {@link java.util.Optional} type.
         *
         * @param val true if list type
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation optionalWrapped(boolean val) {
            ipInfoBuilder.optionalWrapped(val);
            return this;
        }

        /**
         * Sets the optional qualified name of the injection point.
         *
         * @param val the name
         * @return the builder
         */
        public BuilderContinuation named(String val) {
            ipInfoBuilder.addQualifier(QualifierAndValueDefault.createNamed(val));
            return this;
        }

        /**
         * Sets the optional qualifier of the injection point.
         *
         * @param val the qualifier
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation addQualifier(Class<? extends Annotation> val) {
            ipInfoBuilder.addQualifier(QualifierAndValueDefault.create(val));
            return this;
        }

        /**
         * Sets the optional qualifier of the injection point.
         *
         * @param val the qualifier
         * @return the builder
         */
        // note: called from generated code
        public BuilderContinuation addQualifier(QualifierAndValue val) {
            ipInfoBuilder.addQualifier(val);
            return this;
        }

        /**
         * Sets the optional qualifier of the injection point.
         *
         * @param val the qualifier
         * @return the builder
         */
        public BuilderContinuation qualifiers(Collection<QualifierAndValue> val) {
            ipInfoBuilder.qualifiers(val);
            return this;
        }

        /**
         * Sets the flag indicating that the injection point is static.
         *
         * @param val flag indicating if static
         * @return the builder
         */
        public BuilderContinuation staticDeclaration(boolean val) {
            ipInfoBuilder.staticDeclaration(val);
            return this;
        }

        /**
         * Commits the last dependency item, and prepares for the next.
         *
         * @return the builder
         */
        public DependenciesInfo build() {
            assert (builder != null);

            commitLastDependency();
            DependenciesInfo deps = builder.build();
            builder = null;
            return deps;
        }

        /**
         * Adds a new dependency item.
         *
         * @param serviceTypeName the service type
         * @param elemName        the element name
         * @param elemTypeName    the element type
         * @param kind            the element kind
         * @param elemArgs        used for methods only; this is the number of arguments the method accepts
         * @param access          the element access
         * @return the builder
         */
        public BuilderContinuation add(String serviceTypeName,
                                       String elemName,
                                       String elemTypeName,
                                       InjectionPointInfo.ElementKind kind,
                                       int elemArgs,
                                       InjectionPointInfo.Access access) {
            commitLastDependency();

            // thus begins a new builder continuation round
            ipInfoBuilder = InjectionPointInfoDefault.builder()
                    .serviceTypeName(serviceTypeName)
                    .access(access)
                    .elementKind(kind)
                    .elementTypeName(elemTypeName)
                    .elementName(elemName)
                    .elementOffset(Optional.ofNullable(ElementInfo.ElementKind.FIELD == kind ? null : 0))
                    .elementArgs(elemArgs);
            return this;
        }

        /**
         * Commits the last dependency item to complete the last builder continuation.
         *
         * @return any built dependencies info realized from this last commit
         */
        // note: called from generated code
        public Optional<DependencyInfo> commitLastDependency() {
            String id = null;
            try {
                assert (builder != null);

                if (ipInfoBuilder != null) {
                    id = toId(ipInfoBuilder);
                    ipInfoBuilder.baseIdentity(toBaseIdentity(ipInfoBuilder));
                    ipInfoBuilder.id(id);
                    ServiceInfoCriteria criteria = ServiceInfoCriteriaDefault.builder()
                            .addContractImplemented(ipInfoBuilder.elementTypeName())
                            .qualifiers(ipInfoBuilder.qualifiers())
                            .build();
                    InjectionPointInfo ipInfo = ipInfoBuilder
                            .dependencyToServiceInfo(criteria)
                            .build();
                    ipInfoBuilder = null;

                    DependencyInfo dep = DependencyInfoDefault.builder()
                            .addInjectionPointDependency(ipInfo)
                            .dependencyTo(ipInfo.dependencyToServiceInfo())
                            .build();
                    builder.addServiceInfoDependency(ipInfo.dependencyToServiceInfo(), dep);
                    return Optional.of(dep);
                }

                return Optional.empty();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to commit a dependency for id: " + id, e);
            }
        }
    }

}
