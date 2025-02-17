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

package io.helidon.pico.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.builder.processor.tools.BuilderTypeTools;
import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.DependencyInfo;
import io.helidon.pico.api.ElementInfo;
import io.helidon.pico.api.InjectionPointInfo;
import io.helidon.pico.api.InjectionPointInfoDefault;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.QualifierAndValue;
import io.helidon.pico.api.QualifierAndValueDefault;
import io.helidon.pico.api.RunLevel;
import io.helidon.pico.api.ServiceInfo;
import io.helidon.pico.api.ServiceInfoBasics;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceInfoDefault;
import io.helidon.pico.runtime.AbstractServiceProvider;
import io.helidon.pico.runtime.Dependencies;
import io.helidon.pico.tools.spi.ActivatorCreator;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import jakarta.inject.Singleton;

import static io.helidon.common.types.TypeNameDefault.create;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;
import static io.helidon.pico.api.ServiceInfoBasics.DEFAULT_PICO_WEIGHT;
import static io.helidon.pico.tools.CommonUtils.first;
import static io.helidon.pico.tools.CommonUtils.hasValue;
import static io.helidon.pico.tools.CommonUtils.toFlatName;
import static io.helidon.pico.tools.CommonUtils.toSet;
import static io.helidon.pico.tools.TypeTools.componentTypeNameOf;
import static io.helidon.pico.tools.TypeTools.createTypeNameFromClassInfo;
import static io.helidon.pico.tools.TypeTools.isPackagePrivate;

/**
 * Responsible for building all pico-di related collateral for a module, including:
 * <ol>
 * <li>The {@link io.helidon.pico.api.ServiceProvider} for each service type implementation passed in.
 * <li>The {@link io.helidon.pico.api.Activator} and {@link io.helidon.pico.api.DeActivator} for each service type implementation passed in.
 * <li>The {@link io.helidon.pico.api.ModuleComponent} for the aggregate service provider bindings for the same set of service type names.
 * <li>The module-info as appropriate for the above set of services (and contracts).
 * <li>The /META-INF/services entries as appropriate.
 * </ol>
 *
 * This API can also be used to only produce meta-information describing the model without the codegen option - see
 * {@link ActivatorCreatorRequest#codeGenPaths()} for details.
 */
@Singleton
@Weight(DEFAULT_PICO_WEIGHT)
public class ActivatorCreatorDefault extends AbstractCreator implements ActivatorCreator, Weighted {
    /**
     * The suffix name for the service type activator class.
     */
    static final String ACTIVATOR_NAME_SUFFIX = "Activator";
    static final String INNER_ACTIVATOR_CLASS_NAME = "$$" + NAME_PREFIX + ACTIVATOR_NAME_SUFFIX;
    private static final String SERVICE_PROVIDER_ACTIVATOR_HBS = "service-provider-activator.hbs";
    private static final String SERVICE_PROVIDER_APPLICATION_STUB_HBS = "service-provider-application-stub.hbs";
    private static final String SERVICE_PROVIDER_MODULE_HBS = "service-provider-module.hbs";

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public ActivatorCreatorDefault() {
        super(TemplateHelper.DEFAULT_TEMPLATE_NAME);
    }

    @Override
    public ActivatorCreatorResponse createModuleActivators(ActivatorCreatorRequest req) throws ToolsException {
        String templateName = (hasValue(req.templateName())) ? req.templateName() : templateName();

        ActivatorCreatorResponseDefault.Builder builder = ActivatorCreatorResponseDefault.builder()
                .configOptions(req.configOptions())
                .templateName(templateName);

        if (req.serviceTypeNames().isEmpty()) {
            return handleError(req, new ToolsException("ServiceTypeNames is required to be passed"), builder);
        }

        try {
            LazyValue<ScanResult> scan = LazyValue.create(ReflectionHandler.INSTANCE::scan);
            return codegen(req, builder, scan);
        } catch (ToolsException te) {
            return handleError(req, te, builder);
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            return handleError(req, new ToolsException("Failed in create", t), builder);
        }
    }

    ActivatorCreatorResponse codegen(ActivatorCreatorRequest req,
                                     ActivatorCreatorResponseDefault.Builder builder,
                                     LazyValue<ScanResult> scan) {
        boolean isApplicationPreCreated = req.configOptions().isApplicationPreCreated();
        boolean isModuleCreated = req.configOptions().isModuleCreated();
        CodeGenPaths codeGenPaths = req.codeGenPaths();
        Map<TypeName, Boolean> serviceTypeToIsAbstractType = req.codeGen().serviceTypeIsAbstractTypes();
        List<TypeName> activatorTypeNames = new ArrayList<>();
        List<TypeName> activatorTypeNamesPutInModule = new ArrayList<>();
        Map<TypeName, ActivatorCodeGenDetail> activatorDetails = new LinkedHashMap<>();
        for (TypeName serviceTypeName : req.serviceTypeNames()) {
            try {
                ActivatorCodeGenDetail activatorDetail = createActivatorCodeGenDetail(req, serviceTypeName, scan);
                Object prev = activatorDetails.put(serviceTypeName, activatorDetail);
                assert (prev == null);
                codegenActivatorFilerOut(req, activatorDetail);
                TypeName activatorTypeName = toActivatorImplTypeName(serviceTypeName);
                activatorTypeNames.add(activatorTypeName);
                Boolean isAbstract = serviceTypeToIsAbstractType.get(serviceTypeName);
                isAbstract = (isAbstract != null) && isAbstract;
                if (!isAbstract) {
                    activatorTypeNamesPutInModule.add(activatorTypeName);
                }

                InterceptionPlan interceptionPlan = req.codeGen().serviceTypeInterceptionPlan().get(serviceTypeName);
                if (interceptionPlan != null) {
                    codegenInterceptorFilerOut(req, builder, interceptionPlan);
                }
            } catch (Exception e) {
                throw new ToolsException("Failed to process: " + serviceTypeName, e);
            }
        }
        builder.serviceTypeNames(activatorTypeNames)
                .serviceTypeDetails(activatorDetails);

        ModuleDetail moduleDetail;
        TypeName applicationTypeName;
        Map<String, List<String>> metaInfServices;
        TypeName moduleTypeName = toModuleTypeName(req, activatorTypeNames);
        if (moduleTypeName != null) {
            String className = ApplicationCreatorDefault
                    .toApplicationClassName(req.codeGen().classPrefixName());
            applicationTypeName = create(moduleTypeName.packageName(), className);
            builder.applicationTypeName(applicationTypeName);
            String applicationStub = toApplicationStubBody(req, applicationTypeName, req.moduleName().orElse(null));
            if (isApplicationPreCreated && isModuleCreated) {
                codegenApplicationFilerOut(req, applicationTypeName, applicationStub);
            }

            moduleDetail = toModuleDetail(req,
                                          activatorTypeNamesPutInModule,
                                          moduleTypeName,
                                          applicationTypeName,
                                          isApplicationPreCreated,
                                          isModuleCreated);
            builder.moduleDetail(moduleDetail);
            if (moduleDetail != null && isModuleCreated) {
                codegenModuleFilerOut(req, moduleDetail);
                Path outPath = codegenModuleInfoFilerOut(req, moduleDetail.descriptor().orElseThrow());
                logger().log(System.Logger.Level.DEBUG, "codegen module-info written to: " + outPath);
            }

            metaInfServices = toMetaInfServices(moduleDetail,
                                                applicationTypeName,
                                                isApplicationPreCreated,
                                                isModuleCreated);
            builder.metaInfServices(metaInfServices);
            if (!metaInfServices.isEmpty() && req.configOptions().isModuleCreated()) {
                codegenMetaInfServices(req, codeGenPaths, metaInfServices);
            }
        }

        return builder.build();
    }

    private ModuleDetail toModuleDetail(ActivatorCreatorRequest req,
                                        List<TypeName> activatorTypeNamesPutInModule,
                                        TypeName moduleTypeName,
                                        TypeName applicationTypeName,
                                        boolean isApplicationCreated,
                                        boolean isModuleCreated) {
        String className = moduleTypeName.className();
        String packageName = moduleTypeName.packageName();
        String moduleName = req.moduleName().orElse(null);

        ActivatorCreatorCodeGen codeGen = req.codeGen();
        String typePrefix = codeGen.classPrefixName();
        Collection<String> modulesRequired = codeGen.modulesRequired();
        Map<TypeName, Set<TypeName>> serviceTypeContracts = codeGen.serviceTypeContracts();
        Map<TypeName, Set<TypeName>> externalContracts = codeGen.serviceTypeExternalContracts();

        Optional<String> moduleInfoPath = req.codeGenPaths().moduleInfoPath();
        ModuleInfoCreatorRequest moduleCreatorRequest = ModuleInfoCreatorRequestDefault.builder()
                .name(moduleName)
                .moduleTypeName(moduleTypeName)
                .applicationTypeName(applicationTypeName)
                .modulesRequired(modulesRequired)
                .contracts(serviceTypeContracts)
                .externalContracts(externalContracts)
                .moduleInfoPath(moduleInfoPath)
                .classPrefixName(typePrefix)
                .applicationCreated(isApplicationCreated)
                .moduleCreated(isModuleCreated)
                .build();
        ModuleInfoDescriptor moduleInfo = createModuleInfo(moduleCreatorRequest);
        moduleName = moduleInfo.name();
        String moduleBody = toModuleBody(req, packageName, className, moduleName, activatorTypeNamesPutInModule);
        return ModuleDetailDefault.builder()
                .moduleName(moduleName)
                .moduleTypeName(moduleTypeName)
                .serviceProviderActivatorTypeNames(activatorTypeNamesPutInModule)
                .moduleBody(moduleBody)
                .moduleInfoBody(moduleInfo.contents())
                .descriptor(moduleInfo)
                .build();
    }

    /**
     * Applies to module-info.
     */
    static TypeName toModuleTypeName(ActivatorCreatorRequest req,
                                     List<TypeName> activatorTypeNames) {
        String packageName;
        if (hasValue(req.packageName().orElse(null))) {
            packageName = req.packageName().orElseThrow();
        } else {
            if (activatorTypeNames == null || activatorTypeNames.isEmpty()) {
                return null;
            }
            packageName = activatorTypeNames.get(0).packageName() + "." + NAME_PREFIX;
        }

        String className = toModuleClassName(req.codeGen().classPrefixName());
        return create(packageName, className);
    }

    static String toModuleClassName(String modulePrefix) {
        modulePrefix = (modulePrefix == null) ? "" : modulePrefix;
        return NAME_PREFIX + modulePrefix + MODULE_NAME_SUFFIX;
    }

    static Map<String, List<String>> toMetaInfServices(ModuleDetail moduleDetail,
                                                       TypeName applicationTypeName,
                                                       boolean isApplicationCreated,
                                                       boolean isModuleCreated) {
        Map<String, List<String>> metaInfServices = new LinkedHashMap<>();
        if (isApplicationCreated && applicationTypeName != null) {
            metaInfServices.put(TypeNames.PICO_APPLICATION,
                                List.of(applicationTypeName.name()));
        }
        if (isModuleCreated && moduleDetail != null) {
            metaInfServices.put(TypeNames.PICO_MODULE,
                                List.of(moduleDetail.moduleTypeName().name()));
        }
        return metaInfServices;
    }

    void codegenMetaInfServices(GeneralCreatorRequest req,
                                CodeGenPaths paths,
                                Map<String, List<String>> metaInfServices) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            req.filer().codegenMetaInfServices(paths, metaInfServices);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    void codegenActivatorFilerOut(GeneralCreatorRequest req,
                                  ActivatorCodeGenDetail activatorDetail) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            req.filer().codegenActivatorFilerOut(activatorDetail);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    void codegenModuleFilerOut(GeneralCreatorRequest req,
                               ModuleDetail moduleDetail) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            req.filer().codegenModuleFilerOut(moduleDetail);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    void codegenApplicationFilerOut(GeneralCreatorRequest req,
                                    TypeName applicationTypeName,
                                    String applicationBody) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            req.filer().codegenApplicationFilerOut(applicationTypeName, applicationBody);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    Path codegenModuleInfoFilerOut(GeneralCreatorRequest req,
                                   ModuleInfoDescriptor descriptor) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            return req.filer().codegenModuleInfoFilerOut(descriptor, true).orElse(null);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    @Override
    public InterceptorCreatorResponse codegenInterceptors(GeneralCreatorRequest req,
                                                          Map<TypeName, InterceptionPlan> interceptionPlans) {
        InterceptorCreatorResponseDefault.Builder res = InterceptorCreatorResponseDefault.builder();
        res.interceptionPlans(interceptionPlans);

        for (Map.Entry<TypeName, InterceptionPlan> e : interceptionPlans.entrySet()) {
            try {
                Path filePath = codegenInterceptorFilerOut(req, null, e.getValue());
                res.addGeneratedFile(e.getKey(), filePath);
            } catch (Throwable t) {
                throw new ToolsException("Failed while processing: " + e.getKey(), t);
            }
        }

        return res.build();
    }

    private Path codegenInterceptorFilerOut(GeneralCreatorRequest req,
                                            ActivatorCreatorResponseDefault.Builder builder,
                                            InterceptionPlan interceptionPlan) {
        validate(interceptionPlan);
        TypeName interceptorTypeName = InterceptorCreatorDefault.createInterceptorSourceTypeName(interceptionPlan);
        InterceptorCreatorDefault interceptorCreator = new InterceptorCreatorDefault();
        String body = interceptorCreator.createInterceptorSourceBody(interceptionPlan);
        if (builder != null) {
            builder.addServiceTypeInterceptorPlan(interceptorTypeName, interceptionPlan);
        }
        return req.filer().codegenJavaFilerOut(interceptorTypeName, body).orElseThrow();
    }

    private void validate(InterceptionPlan plan) {
        List<ElementInfo> ctorElements = plan.interceptedElements().stream()
                .map(InterceptedElement::elementInfo)
                .filter(it -> it.elementKind() == ElementInfo.ElementKind.CONSTRUCTOR)
                .collect(Collectors.toList());
        if (ctorElements.size() > 1) {
            throw new IllegalStateException("Can only have interceptor with a single (injectable) constructor for: "
                                                    + plan.interceptedService().serviceTypeName());
        }
    }

    private ActivatorCodeGenDetail createActivatorCodeGenDetail(ActivatorCreatorRequest req,
                                                                TypeName serviceTypeName,
                                                                LazyValue<ScanResult> scan) {
        ActivatorCreatorCodeGen codeGen = req.codeGen();
        String template = templateHelper().safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_ACTIVATOR_HBS);
        ServiceInfoBasics serviceInfo = toServiceInfo(serviceTypeName, codeGen);
        TypeName activatorTypeName = toActivatorTypeName(serviceTypeName);
        TypeName parentTypeName = toParentTypeName(serviceTypeName, codeGen);
        String activatorGenericDecl = toActivatorGenericDecl(serviceTypeName, codeGen);
        DependenciesInfo dependencies = toDependencies(serviceTypeName, codeGen);
        DependenciesInfo parentDependencies = toDependencies(parentTypeName, codeGen);
        Set<String> scopeTypeNames = toScopeTypeNames(serviceTypeName, codeGen);
        String generatedSticker = toGeneratedSticker(req);
        List<String> description = toDescription(serviceTypeName);
        Double weightedPriority = toWeightedPriority(serviceTypeName, codeGen);
        Integer runLevel = toRunLevel(serviceTypeName, codeGen);
        String postConstructMethodName = toPostConstructMethodName(serviceTypeName, codeGen);
        String preDestroyMethodName = toPreDestroyMethodName(serviceTypeName, codeGen);
        List<?> serviceTypeInjectionOrder = toServiceTypeHierarchy(serviceTypeName, codeGen, scan);
        List<String> extraCodeGen = toExtraCodeGen(serviceTypeName, codeGen);
        List<String> extraClassComments = toExtraClassComments(serviceTypeName, codeGen);
        boolean isProvider = toIsProvider(serviceTypeName, codeGen);
        boolean isConcrete = toIsConcrete(serviceTypeName, codeGen);
        boolean isSupportsJsr330InStrictMode = req.configOptions().isSupportsJsr330InStrictMode();
        Collection<Object> injectionPointsSkippedInParent =
                toCodegenInjectMethodsSkippedInParent(isSupportsJsr330InStrictMode, activatorTypeName, codeGen, scan);

        ActivatorCreatorArgs args = ActivatorCreatorArgsDefault.builder()
                .template(template)
                .serviceTypeName(serviceTypeName)
                .activatorTypeName(activatorTypeName)
                .activatorGenericDecl(Optional.ofNullable(activatorGenericDecl))
                .parentTypeName(Optional.ofNullable(parentTypeName))
                .scopeTypeNames(scopeTypeNames)
                .description(description)
                .serviceInfo(serviceInfo)
                .dependencies(Optional.ofNullable(dependencies))
                .parentDependencies(Optional.ofNullable(parentDependencies))
                .injectionPointsSkippedInParent(injectionPointsSkippedInParent)
                .serviceTypeInjectionOrder(serviceTypeInjectionOrder)
                .generatedSticker(generatedSticker)
                .weightedPriority(Optional.ofNullable(weightedPriority))
                .runLevel(Optional.ofNullable(runLevel))
                .postConstructMethodName(Optional.ofNullable(postConstructMethodName))
                .preDestroyMethodName(Optional.ofNullable(preDestroyMethodName))
                .extraCodeGen(extraCodeGen)
                .extraClassComments(extraClassComments)
                .concrete(isConcrete)
                .provider(isProvider)
                .supportsJsr330InStrictMode(isSupportsJsr330InStrictMode)
                .build();
        String activatorBody = toActivatorBody(args);

        return ActivatorCodeGenDetailDefault.builder()
                .serviceInfo(serviceInfo)
                .dependencies(Optional.ofNullable(dependencies))
                .serviceTypeName(toActivatorImplTypeName(activatorTypeName))
                .body(activatorBody)
                .build();
    }

    /**
     * Creates a payload given the batch of services to process.
     *
     * @param services the services to process
     * @return the payload, or empty if unable or nothing to process
     */
    public static Optional<ActivatorCreatorCodeGen> createActivatorCreatorCodeGen(ServicesToProcess services) {
        // do not generate activators for modules or applications...
        List<TypeName> serviceTypeNames = services.serviceTypeNames();
        if (!serviceTypeNames.isEmpty()) {
            TypeName applicationTypeName = createFromTypeName(TypeNames.PICO_APPLICATION);
            TypeName moduleTypeName = createFromTypeName(TypeNames.PICO_MODULE);
            serviceTypeNames = serviceTypeNames.stream()
                    .filter(typeName -> {
                        Set<TypeName> contracts = services.contracts().get(typeName);
                        if (contracts == null) {
                            return true;
                        }
                        return !contracts.contains(applicationTypeName) && !contracts.contains(moduleTypeName);
                    })
                    .collect(Collectors.toList());
        }
        if (serviceTypeNames.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ActivatorCreatorCodeGenDefault.builder()
                .serviceTypeToParentServiceTypes(toFilteredParentServiceTypes(services))
                .serviceTypeToActivatorGenericDecl(services.activatorGenericDecls())
                .serviceTypeHierarchy(toFilteredHierarchy(services))
                .serviceTypeAccessLevels(services.accessLevels())
                .serviceTypeIsAbstractTypes(services.isAbstractMap())
                .serviceTypeContracts(toFilteredContracts(services))
                .serviceTypeExternalContracts(services.externalContracts())
                .serviceTypeInjectionPointDependencies(services.injectionPointDependencies())
                .serviceTypePostConstructMethodNames(services.postConstructMethodNames())
                .serviceTypePreDestroyMethodNames(services.preDestroyMethodNames())
                .serviceTypeWeights(services.weightedPriorities())
                .serviceTypeRunLevels(services.runLevels())
                .serviceTypeScopeNames(services.scopeTypeNames())
                .serviceTypeToProviderForTypes(services.providerForTypeNames())
                .serviceTypeQualifiers(services.qualifiers())
                .modulesRequired(services.requiredModules())
                .classPrefixName((services.lastKnownTypeSuffix() != null)
                                         ? ApplicationCreatorDefault.upperFirstChar(services.lastKnownTypeSuffix())
                                         : ActivatorCreatorCodeGen.DEFAULT_CLASS_PREFIX_NAME)
                .serviceTypeInterceptionPlan(services.interceptorPlans())
                .extraCodeGen(services.extraCodeGen())
                .extraClassComments(services.extraActivatorClassComments())
                .build());
    }

    /**
     * Create a request based upon the contents of services to processor.
     *
     * @param servicesToProcess the batch being processed
     * @param codeGen           the code gen request
     * @param configOptions     the config options
     * @param filer             the filer
     * @param throwIfError      fail on error?
     * @return the activator request instance
     */
    public static ActivatorCreatorRequest createActivatorCreatorRequest(ServicesToProcess servicesToProcess,
                                                                        ActivatorCreatorCodeGen codeGen,
                                                                        ActivatorCreatorConfigOptions configOptions,
                                                                        CodeGenFiler filer,
                                                                        boolean throwIfError) {
        String packageName = servicesToProcess.determineGeneratedPackageName();
        String moduleName = servicesToProcess.determineGeneratedModuleName();
        if (ModuleInfoDescriptor.DEFAULT_MODULE_NAME.equals(moduleName)) {
            // last resort is using the application name as the module name
            moduleName = packageName;
        }

        CodeGenPaths codeGenPaths = createCodeGenPaths(servicesToProcess);
        return ActivatorCreatorRequestDefault.builder()
                .serviceTypeNames(servicesToProcess.serviceTypeNames())
                .codeGen(codeGen)
                .codeGenPaths(codeGenPaths)
                .filer(filer)
                .configOptions(configOptions)
                .throwIfError(throwIfError)
                .moduleName(moduleName)
                .packageName(packageName)
                .build();
    }

    private static Map<TypeName, TypeName> toFilteredParentServiceTypes(ServicesToProcess services) {
        Map<TypeName, TypeName> parents = services.parentServiceTypes();
        Map<TypeName, TypeName> filteredParents = new LinkedHashMap<>(parents);
        for (Map.Entry<TypeName, TypeName> e : parents.entrySet()) {
            if (e.getValue() != null
                    && !services.serviceTypeNames().contains(e.getValue())
                    // if the caller is declaring a parent with generics, then assume they know what they are doing
                    && !e.getValue().fqName().contains("<")) {
                TypeName serviceTypeName = e.getKey();
                if (services.activatorGenericDecls().get(serviceTypeName) == null) {
                    filteredParents.put(e.getKey(), null);
                }
            }
        }
        return filteredParents;
    }

    private static Map<TypeName, List<TypeName>> toFilteredHierarchy(ServicesToProcess services) {
        Map<TypeName, List<TypeName>> hierarchy = services.serviceTypeToHierarchy();
        Map<TypeName, List<TypeName>> filteredHierarchy = new LinkedHashMap<>();
        for (Map.Entry<TypeName, List<TypeName>> e : hierarchy.entrySet()) {
            List<TypeName> filtered = e.getValue().stream()
                    .filter((typeName) -> services.serviceTypeNames().contains(typeName))
                    .collect(Collectors.toList());
//            assert (!filtered.isEmpty()) : e;
            filteredHierarchy.put(e.getKey(), filtered);
        }
        return filteredHierarchy;
    }

    private static Map<TypeName, Set<TypeName>> toFilteredContracts(ServicesToProcess services) {
        Map<TypeName, Set<TypeName>> contracts = services.contracts();
        Map<TypeName, Set<TypeName>> filteredContracts = new LinkedHashMap<>();
        for (Map.Entry<TypeName, Set<TypeName>> e : contracts.entrySet()) {
            Set<TypeName> contractsForThisService = e.getValue();
            Set<TypeName> externalContractsForThisService = services.externalContracts().get(e.getKey());
            if (externalContractsForThisService == null || externalContractsForThisService.isEmpty()) {
                filteredContracts.put(e.getKey(), e.getValue());
            } else {
                Set<TypeName> filteredContractsForThisService = new LinkedHashSet<>(contractsForThisService);
                filteredContractsForThisService.removeAll(externalContractsForThisService);
                filteredContracts.put(e.getKey(), filteredContractsForThisService);
            }
        }
        return filteredContracts;
    }

    String toApplicationStubBody(ActivatorCreatorRequest req,
                                 TypeName applicationTypeName,
                                 String moduleName) {
        String template = templateHelper().safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_APPLICATION_STUB_HBS);

        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", applicationTypeName.className());
        subst.put("packagename", applicationTypeName.packageName());
        subst.put("description", "Generated " + PicoServicesConfig.NAME + " Application.");
        subst.put("generatedanno", toGeneratedSticker(req));
        subst.put("header", BuilderTypeTools.copyrightHeaderFor(getClass().getName()));
        subst.put("modulename", moduleName);
        return templateHelper().applySubstitutions(template, subst, true).trim();
    }

    String toModuleBody(ActivatorCreatorRequest req,
                        String packageName,
                        String className,
                        String moduleName,
                        List<TypeName> activatorTypeNames) {
        String template = templateHelper().safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_MODULE_HBS);

        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", className);
        subst.put("packagename", packageName);
        subst.put("description", "Generated " + PicoServicesConfig.NAME + " Module.");
        subst.put("generatedanno", toGeneratedSticker(req));
        subst.put("header", BuilderTypeTools.copyrightHeaderFor(getClass().getName()));
        subst.put("modulename", moduleName);
        subst.put("activators", activatorTypeNames);

        return templateHelper().applySubstitutions(template, subst, true).trim();
    }

    @Override
    public TypeName toActivatorImplTypeName(TypeName serviceTypeName) {
        return create(serviceTypeName.packageName(),
                                      toFlatName(serviceTypeName.className())
                                              + INNER_ACTIVATOR_CLASS_NAME);
    }

    private String toActivatorBody(ActivatorCreatorArgs args) {
        Map<String, Object> subst = new HashMap<>();
        subst.put("header", BuilderTypeTools.copyrightHeaderFor(getClass().getName()));
        subst.put("activatorsuffix", INNER_ACTIVATOR_CLASS_NAME);
        subst.put("classname", args.activatorTypeName().className());
        subst.put("flatclassname", toFlatName(args.activatorTypeName().className()));
        subst.put("packagename", args.activatorTypeName().packageName());
        subst.put("activatorgenericdecl", args.activatorGenericDecl().orElse(null));
        subst.put("parent", toCodenParent(args.isSupportsJsr330InStrictMode(),
                                          args.activatorTypeName(), args.parentTypeName().orElse(null)));
        subst.put("scopetypenames", args.scopeTypeNames());
        subst.put("description", args.description());
        subst.put("generatedanno", args.generatedSticker());
        subst.put("isprovider", args.isProvider());
        subst.put("isconcrete", args.isConcrete());
        subst.put("contracts", args.serviceInfo().contractsImplemented());
        if (args.serviceInfo() instanceof ServiceInfo) {
            ServiceInfo serviceInfo = ((ServiceInfo) args.serviceInfo());
            Set<String> extContracts = serviceInfo.externalContractsImplemented();
            subst.put("externalcontracts", extContracts);
            // there is no need to list these twice, since external contracts will implicitly back-full into contracts
            subst.put("contracts", args.serviceInfo().contractsImplemented().stream()
                    .filter(it -> !extContracts.contains(it)).collect(Collectors.toList()));
        }
        subst.put("qualifiers", toCodegenQualifiers(args.serviceInfo().qualifiers()));
        subst.put("dependencies", toCodegenDependencies(args.dependencies().orElse(null)));
        subst.put("weight", args.weightedPriority().orElse(null));
        subst.put("isweightset", args.weightedPriority().isPresent());
        subst.put("runlevel", args.runLevel().orElse(null));
        subst.put("isrunlevelset", args.runLevel().isPresent());
        subst.put("postconstruct", args.postConstructMethodName().orElse(null));
        subst.put("predestroy", args.preDestroyMethodName().orElse(null));
        subst.put("ctorarglist", toCodegenCtorArgList(args.dependencies().orElse(null)));
        subst.put("ctorargs", toCodegenInjectCtorArgs(args.dependencies().orElse(null)));
        subst.put("injectedfields", toCodegenInjectFields(args.dependencies().orElse(null)));
        subst.put("injectedmethods", toCodegenInjectMethods(args.activatorTypeName(), args.dependencies().orElse(null)));
        subst.put("injectedmethodsskippedinparent", args.injectionPointsSkippedInParent());
        subst.put("extracodegen", args.extraCodeGen());
        subst.put("extraclasscomments", args.extraClassComments());
        subst.put("injectionorder", args.serviceTypeInjectionOrder());
        subst.put("issupportsjsr330instrictmode", args.isSupportsJsr330InStrictMode());

        logger().log(System.Logger.Level.DEBUG, "dependencies for "
                + args.serviceTypeName() + " == " + args.dependencies());

        return templateHelper().applySubstitutions(args.template(), subst, true).trim();
    }

    String toCodenParent(boolean ignoredIsSupportsJsr330InStrictMode,
                         TypeName activatorTypeName,
                         TypeName parentTypeName) {
        String result;
        if (parentTypeName == null || Object.class.getName().equals(parentTypeName.name())) {
            result = AbstractServiceProvider.class.getName() + "<" + activatorTypeName.className() + ">";
        } else if (parentTypeName.typeArguments() == null || parentTypeName.typeArguments().isEmpty()) {
            result = parentTypeName.packageName()
                    + (parentTypeName.packageName() == null ? "" : ".")
                    + parentTypeName.className().replace(".", "$")
                    + INNER_ACTIVATOR_CLASS_NAME;
        } else {
            result = parentTypeName.fqName();
        }

        return result;
    }

    List<String> toCodegenDependencies(DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        List<String> result = new ArrayList<>();
        dependencies.allDependencies()
                .forEach(dep1 -> dep1.injectionPointDependencies()
                        .forEach(dep2 -> result.add(toCodegenDependency(dep1.dependencyTo(), dep2))));

        return result;
    }

    String toCodegenDependency(ServiceInfoCriteria dependencyTo,
                               InjectionPointInfo ipInfo) {
        StringBuilder builder = new StringBuilder();
        //.add("world", World.class, InjectionPointInfo.ElementKind.FIELD, InjectionPointInfo.Access.PACKAGE_PRIVATE)
        String elemName = CodeGenUtils.elementNameKindRef(ipInfo.elementName(), ipInfo.elementKind());
        builder.append(".add(").append(elemName).append(", ");
        builder.append(Objects.requireNonNull(componentTypeNameOf(first(dependencyTo.contractsImplemented(), true))))
                .append(".class, ");
        builder.append("ElementKind.").append(Objects.requireNonNull(ipInfo.elementKind())).append(", ");
        if (InjectionPointInfo.ElementKind.FIELD != ipInfo.elementKind()) {
                builder.append(ipInfo.elementArgs().orElseThrow()).append(", ");
        }
        builder.append("Access.").append(Objects.requireNonNull(ipInfo.access())).append(")");
        Integer elemPos = ipInfo.elementArgs().orElse(null);
        Integer elemOffset = ipInfo.elementOffset().orElse(null);
        Set<QualifierAndValue> qualifiers = ipInfo.qualifiers();
        if (elemPos != null) {
            builder.append(".elemOffset(").append(elemOffset).append(")");
        }
        if (!qualifiers.isEmpty()) {
            builder.append(toCodegenQualifiers(qualifiers));
        }
        if (ipInfo.listWrapped()) {
            builder.append(".listWrapped()");
        }
        if (ipInfo.providerWrapped()) {
            builder.append(".providerWrapped()");
        }
        if (ipInfo.optionalWrapped()) {
            builder.append(".optionalWrapped()");
        }
        if (ipInfo.staticDeclaration()) {
            builder.append(".staticDeclaration()");
        }
        return builder.toString();
    }

    String toCodegenQualifiers(Collection<QualifierAndValue> qualifiers) {
        StringBuilder builder = new StringBuilder();
        for (QualifierAndValue qualifier : qualifiers) {
            if (builder.length() > 0) {
                builder.append("\n\t\t\t");
            }
            builder.append(".addQualifier(").append(toCodegenQualifiers(qualifier)).append(")");
        }
        return builder.toString();
    }

    String toCodegenQualifiers(QualifierAndValue qualifier) {
        String val = toCodegenQuotedString(qualifier.value().orElse(null));
        String result = QualifierAndValueDefault.class.getName() + ".create("
                + qualifier.qualifierTypeName() + ".class";
        if (val != null) {
            result += ", " + val;
        }
        result += ")";
        return result;
    }

    String toCodegenQuotedString(String value) {
        return (value == null) ? null : "\"" + value + "\"";
    }

    String toCodegenDecl(ServiceInfoCriteria dependencyTo,
                         InjectionPointInfo injectionPointInfo) {
        String contract = first(dependencyTo.contractsImplemented(), true);
        StringBuilder builder = new StringBuilder();
        if (injectionPointInfo.optionalWrapped()) {
            builder.append("Optional<").append(contract).append(">");
        } else {
            if (injectionPointInfo.listWrapped()) {
                builder.append("List<");
            }
            if (injectionPointInfo.providerWrapped()) {
                builder.append("Provider<");
            }
            builder.append(contract);
            if (injectionPointInfo.providerWrapped()) {
                builder.append(">");
            }
            if (injectionPointInfo.listWrapped()) {
                builder.append(">");
            }
        }
        PicoSupported.isSupportedInjectionPoint(logger(),
                                                createFromTypeName(injectionPointInfo.serviceTypeName()),
                                                injectionPointInfo,
                                                InjectionPointInfo.Access.PRIVATE == injectionPointInfo.access(),
                                                injectionPointInfo.staticDeclaration());
        return builder.toString();
    }

    String toCodegenCtorArgList(DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> nameRef = new AtomicReference<>();
        List<String> args = new ArrayList<>();
        dependencies.allDependencies()
                .forEach(dep1 -> dep1.injectionPointDependencies()
                        .stream()
                        .filter(dep2 -> InjectionPointInfoDefault.CONSTRUCTOR.equals(dep2.elementName()))
                        .forEach(dep2 -> {
                            if ((nameRef.get() == null)) {
                                nameRef.set(dep2.baseIdentity());
                            } else {
                                assert (nameRef.get().equals(dep2.baseIdentity())) : "only 1 ctor can be injectable";
                            }
                            args.add("c" + count.incrementAndGet());
                        })
        );

        return (args.isEmpty()) ? null : CommonUtils.toString(args);
    }

    List<String> toCodegenInjectCtorArgs(DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> nameRef = new AtomicReference<>();
        List<String> args = new ArrayList<>();
        List<DependencyInfo> allCtorArgs = dependencies.allDependenciesFor(InjectionPointInfoDefault.CONSTRUCTOR);
        allCtorArgs.forEach(dep1 -> dep1.injectionPointDependencies()
                        .forEach(dep2 -> {
                            if (nameRef.get() == null) {
                                nameRef.set(dep2.baseIdentity());
                            } else {
                                assert (nameRef.get().equals(dep2.baseIdentity())) : "only 1 constructor can be injectable";
                            }
                            String cn = toCodegenDecl(dep1.dependencyTo(), dep2);
                            String argName = "c" + count.incrementAndGet();
                            String id = dep2.baseIdentity() + "(" + count.get() + ")";
                            String argBuilder = cn + " "
                                    + argName + " = (" + cn + ") "
                                    + "get(deps, \"" + id + "\");";
                            args.add(argBuilder);
                        }));
        return args;
    }

    List<Object> toCodegenInjectFields(DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        List<Object> fields = new ArrayList<>();
        dependencies.allDependencies()
                .forEach(dep1 -> dep1.injectionPointDependencies().stream()
                        .filter(dep2 -> InjectionPointInfo.ElementKind.FIELD
                                .equals(dep2.elementKind()))
                        .forEach(dep2 -> {
                            String cn = toCodegenDecl(dep1.dependencyTo(), dep2);
                            IdAndToString setter;
                            String id = dep2.id();
                            if (Void.class.getName().equals(cn)) {
                                setter = new IdAndToString(id, dep2.elementName());
                            } else {
                                setter = new IdAndToString(id, dep2.elementName()
                                        + " = (" + cn + ") get(deps, \""
                                        + dep2.baseIdentity() + "\")");
                            }
                            fields.add(setter);
                        }));
        return fields;
    }

    List<Object> toCodegenInjectMethods(TypeName serviceTypeName,
                                        DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        List<Object> methods = new ArrayList<>();
        String lastElemName = null;
        String lastId = null;
        List<String> compositeSetter = null;
        List<DependencyInfo> allDeps = dependencies.allDependencies().stream()
                .filter(it -> it.injectionPointDependencies().iterator().next().elementKind() == ElementInfo.ElementKind.METHOD)
                .collect(Collectors.toList());
        if (allDeps.size() > 1) {
            allDeps.sort(DependenciesInfo.comparator());
        }

        for (DependencyInfo dep1 : allDeps) {
            for (InjectionPointInfo ipInfo : dep1.injectionPointDependencies()) {
                if (ipInfo.elementKind() != InjectionPointInfo.ElementKind.METHOD) {
                    continue;
                }

                String id = toBaseIdTagName(ipInfo, serviceTypeName);
                String elemName = ipInfo.elementName();
                Integer elemPos = ipInfo.elementOffset().orElse(null);
                int elemArgs = ipInfo.elementArgs().orElse(0);
                String cn = toCodegenDecl(dep1.dependencyTo(), ipInfo);

                if (lastId != null && !lastId.equals(id) && compositeSetter != null) {
                    IdAndToString setter = new IdAndToString(lastId, lastElemName + "("
                            + CommonUtils.toString(compositeSetter, null, ",\n\t\t\t\t")
                            + ")");
                    methods.add(setter);
                    compositeSetter = null;
                }

                if (0 == elemArgs) {
                    assert (Void.class.getName().equals(cn));
                    IdAndToString setter = new IdAndToString(id, elemName + "()");
                    methods.add(setter);
                } else if (1 == elemArgs) {
                    assert (elemArgs == elemPos);
                    IdAndToString setter = new IdAndToString(id,
                                       elemName + "((" + cn + ") get(deps, \"" + id + "(1)\"))");
                    methods.add(setter);
                } else {
                    assert (elemArgs > 1);
                    if (compositeSetter == null) {
                        compositeSetter = new ArrayList<>();
                    }
                    compositeSetter.add("(" + cn + ") get(deps, \"" + id + "(" + elemPos + ")\")");
                }

                lastId = id;
                lastElemName = elemName;
            }
        }

        if (compositeSetter != null) {
            IdAndToString setter = new IdAndToString(lastId, lastElemName + "("
                    + CommonUtils.toString(compositeSetter, null, ",\n\t\t\t\t")
                    + ")");
            methods.add(setter);
        }

        return methods;
    }

    Collection<Object> toCodegenInjectMethodsSkippedInParent(boolean isSupportsJsr330InStrictMode,
                                                             TypeName serviceTypeName,
                                                             ActivatorCreatorCodeGen codeGen,
                                                             LazyValue<ScanResult> scan) {
        List<TypeName> hierarchy = codeGen.serviceTypeHierarchy().get(serviceTypeName);
        TypeName parent = parentOf(serviceTypeName, codeGen);
        if (hierarchy == null && parent != null) {
            hierarchy = List.of(parent);
        }
        if (hierarchy == null) {
            return List.of();
        }

        DependenciesInfo deps = codeGen.serviceTypeInjectionPointDependencies().get(serviceTypeName);

        Set<Object> result = new LinkedHashSet<>();
        hierarchy.stream().filter((typeName) -> !serviceTypeName.equals(typeName))
                .forEach(parentTypeName -> {
                    DependenciesInfo parentDeps = codeGen.serviceTypeInjectionPointDependencies().get(parentTypeName);
                    List<Object> skipList = toCodegenInjectMethodsSkippedInParent(isSupportsJsr330InStrictMode,
                                                                                  serviceTypeName,
                                                                                  deps,
                                                                                  parentTypeName,
                                                                                  parentDeps,
                                                                                  scan);
                    if (skipList != null) {
                        result.addAll(skipList);
                    }
                });

        return result;
    }

    /**
     * Called in strict Jsr330 compliance mode. If Inject anno is on parent method but not on child method
     * then we should hide the inject in the parent. Crazy that inject was not inherited if you ask me!
     *
     * @param isSupportsJsr330InStrictMode are we in jsr-330 strict mode
     * @param serviceTypeName   the activator service type name
     * @param dependencies      the dependencies for this service type
     * @param parentTypeName    the parent type
     * @param parentDependencies the parent dependencies
     * @param scan              the provider of class introspection
     * @return the list of injection point identifiers that should be skipped in the parent delegation call
     */
    List<Object> toCodegenInjectMethodsSkippedInParent(boolean isSupportsJsr330InStrictMode,
                                                       TypeName serviceTypeName,
                                                       DependenciesInfo dependencies,
                                                       TypeName parentTypeName,
                                                       DependenciesInfo parentDependencies,
                                                       LazyValue<ScanResult> scan) {
        if (!isSupportsJsr330InStrictMode || parentTypeName == null) {
            return null;
        }

        ClassInfo classInfo = toClassInfo(serviceTypeName, scan);
        ClassInfo parentClassInfo = toClassInfo(parentTypeName, scan);
        MethodInfoList parentMethods = parentClassInfo.getDeclaredMethodInfo();
        Map<IdAndToString, MethodInfo> injectedParentMethods = parentMethods.stream()
                .filter(m -> (m.getAnnotationInfo(TypeNames.JAKARTA_INJECT) != null))
                .filter(m -> ExternalModuleCreatorDefault.isPicoSupported(parentTypeName, m, logger()))
                .collect(Collectors.toMap(ActivatorCreatorDefault::toBaseIdTag, Function.identity()));
        if (injectedParentMethods.isEmpty()) {
            return null;
        }

        MethodInfoList methods = classInfo.getDeclaredMethodInfo();
        Map<IdAndToString, MethodInfo> allSupportedMethodsOnServiceType = methods.stream()
                .filter(m -> ExternalModuleCreatorDefault.isPicoSupported(serviceTypeName, m, logger()))
                .collect(Collectors.toMap(ActivatorCreatorDefault::toBaseIdTag, Function.identity()));

        List<Object> removeList = null;

        for (Map.Entry<IdAndToString, MethodInfo> e : injectedParentMethods.entrySet()) {
            MethodInfo method = allSupportedMethodsOnServiceType.get(e.getKey());
            if (method != null) {
                AnnotationInfo annotationInfo = method.getAnnotationInfo(TypeNames.JAKARTA_INJECT);
                if (annotationInfo != null) {
                    continue;
                }
                if (removeList == null) {
                    removeList = new ArrayList<>();
                }
                removeList.add(e.getKey());
            }
        }

        return removeList;
    }

    static ClassInfo toClassInfo(TypeName serviceTypeName,
                                 LazyValue<ScanResult> scan) {
        ClassInfo classInfo = scan.get().getClassInfo(serviceTypeName.name());
        if (classInfo == null) {
            throw new ToolsException("Unable to introspect: " + serviceTypeName);
        }
        return classInfo;
    }

    static IdAndToString toBaseIdTag(MethodInfo m) {
        String packageName = m.getClassInfo().getPackageName();
        boolean isPackagePrivate = isPackagePrivate(m.getModifiers());
        InjectionPointInfo.Access access = (isPackagePrivate)
                ? InjectionPointInfo.Access.PACKAGE_PRIVATE : InjectionPointInfo.Access.PUBLIC;
        String idTag = toBaseIdTagName(m.getName(), m.getParameterInfo().length, access, packageName);
        return new IdAndToString(idTag, m);
    }

    static String toBaseIdTagName(InjectionPointInfo ipInfo,
                                  TypeName serviceTypeName) {
        String packageName = serviceTypeName.packageName();
        return toBaseIdTagName(ipInfo.elementName(), ipInfo.elementArgs().orElse(0), ipInfo.access(), packageName);
    }

    static String toBaseIdTagName(String methodName,
                                  int methodArgCount,
                                  InjectionPointInfo.Access access,
                                  String packageName) {
        return Dependencies.toMethodBaseIdentity(methodName, methodArgCount, access, () -> packageName);
    }

    Double toWeightedPriority(TypeName serviceTypeName,
                              ActivatorCreatorCodeGen codeGen) {
        Double weight = codeGen.serviceTypeWeights().get(serviceTypeName);
        if (weight == null && hasParent(serviceTypeName, codeGen)) {
            // we might be a child of another service, in which case we will need to override its value
            weight = Weighted.DEFAULT_WEIGHT;
        }
        return weight;
    }

    Integer toRunLevel(TypeName serviceTypeName,
                       ActivatorCreatorCodeGen codeGen) {
        Integer runLevel = codeGen.serviceTypeRunLevels().get(serviceTypeName);
        if (runLevel == null && hasParent(serviceTypeName, codeGen)) {
            // we might be a child of another service, in which case we will need to override its value
            runLevel = RunLevel.NORMAL;
        }
        return runLevel;
    }

    boolean hasParent(TypeName serviceTypeName,
                      ActivatorCreatorCodeGen codeGen) {
        return (parentOf(serviceTypeName, codeGen) != null);
    }

    TypeName parentOf(TypeName serviceTypeName,
                      ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypeToParentServiceTypes().get(serviceTypeName);
    }

    List<String> toDescription(TypeName serviceTypeName) {
        return List.of("Activator for {@link " + serviceTypeName + "}.");
    }

    TypeName toActivatorTypeName(TypeName serviceTypeName) {
        return serviceTypeName;
    }

    TypeName toParentTypeName(TypeName serviceTypeName,
                              ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypeToParentServiceTypes().get(serviceTypeName);
    }

    String toActivatorGenericDecl(TypeName serviceTypeName,
                                  ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypeToActivatorGenericDecl().get(serviceTypeName);
    }

    Set<String> toScopeTypeNames(TypeName serviceTypeName,
                                 ActivatorCreatorCodeGen codeGen) {
        Set<String> result = codeGen.serviceTypeScopeNames().get(serviceTypeName);
        return (result == null) ? Set.of() : result;
    }

    /**
     * One might expect that isProvider should only be set to true if the service type implements Provider<>. However,
     * that alone would fail JSR-330 testing. The interpretation there is any service without a scope is inferred to be
     * non-singleton, provided/dependent scope.
     */
    boolean toIsProvider(TypeName serviceTypeName,
                         ActivatorCreatorCodeGen codeGen) {
        Set<String> scopeTypeName = toScopeTypeNames(serviceTypeName, codeGen);
        if ((scopeTypeName == null || scopeTypeName.isEmpty()) && toIsConcrete(serviceTypeName, codeGen)) {
            return true;
        }

        Set<TypeName> providerFor = codeGen.serviceTypeToProviderForTypes().get(serviceTypeName);
        return (providerFor != null) && !providerFor.isEmpty();
    }

    boolean toIsConcrete(TypeName serviceTypeName,
                         ActivatorCreatorCodeGen codeGen) {
        Boolean isAbstract = codeGen.serviceTypeIsAbstractTypes().get(serviceTypeName);
        return (isAbstract == null) || !isAbstract;
    }

    /**
     * Creates service info from the service type name and the activator create codegen request.
     *
     * @param serviceTypeName the service type name
     * @param codeGen         the code gen request
     * @return the service info
     */
    public static ServiceInfoBasics toServiceInfo(TypeName serviceTypeName,
                                                  ActivatorCreatorCodeGen codeGen) {
        Set<TypeName> contracts = codeGen.serviceTypeContracts().get(serviceTypeName);
        Set<TypeName> externalContracts = codeGen.serviceTypeExternalContracts().get(serviceTypeName);
        Set<QualifierAndValue> qualifiers = codeGen.serviceTypeQualifiers().get(serviceTypeName);
        return ServiceInfoDefault.builder()
                .serviceTypeName(serviceTypeName.name())
                .contractsImplemented(toSet(contracts, TypeName::name))
                .externalContractsImplemented(toSet(externalContracts, TypeName::name))
                .qualifiers((qualifiers == null) ? Set.of() : qualifiers)
                .build();
    }

    DependenciesInfo toDependencies(TypeName serviceTypeName,
                                    ActivatorCreatorCodeGen codeGen) {
        if (serviceTypeName == null) {
            return null;
        }

        return codeGen.serviceTypeInjectionPointDependencies().get(serviceTypeName);
    }

    String toPostConstructMethodName(TypeName serviceTypeName,
                                     ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypePostConstructMethodNames().get(serviceTypeName);
    }

    String toPreDestroyMethodName(TypeName serviceTypeName,
                                  ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypePreDestroyMethodNames().get(serviceTypeName);
    }

    List<TypeName> toServiceTypeHierarchy(TypeName serviceTypeName,
                                          ActivatorCreatorCodeGen codeGen,
                                          LazyValue<ScanResult> scan) {
        Map<TypeName, List<TypeName>> map = codeGen.serviceTypeHierarchy();
        List<TypeName> order = (map != null) ? map.get(serviceTypeName) : null;
        if (order != null) {
            return (1 == order.size()) ? List.of() : order;
        }

        return serviceTypeHierarchy(serviceTypeName, scan);
    }

    List<String> toExtraCodeGen(TypeName serviceTypeName,
                                ActivatorCreatorCodeGen codeGen) {
        Map<TypeName, List<String>> map = codeGen.extraCodeGen();
        List<String> extraCodeGen = (map != null) ? map.get(serviceTypeName) : List.of();
        return (extraCodeGen == null) ? List.of() : extraCodeGen;
    }

    List<String> toExtraClassComments(TypeName serviceTypeName,
                                      ActivatorCreatorCodeGen codeGen) {
        Map<TypeName, List<String>> map = codeGen.extraClassComments();
        List<String> extraClassComments = (map != null) ? map.get(serviceTypeName) : List.of();
        return (extraClassComments == null) ? List.of() : extraClassComments;
    }

    static List<TypeName> serviceTypeHierarchy(TypeName serviceTypeName,
                                               LazyValue<ScanResult> scan) {
        List<TypeName> order = new ArrayList<>();
        ClassInfo classInfo = toClassInfo(serviceTypeName, scan);
        while (classInfo != null) {
            order.add(0, createTypeNameFromClassInfo(classInfo));
            classInfo = classInfo.getSuperclass();
        }
        return (1 == order.size()) ? List.of() : order;
    }

    ActivatorCreatorResponse handleError(ActivatorCreatorRequest request,
                                         ToolsException e,
                                         ActivatorCreatorResponseDefault.Builder builder) {
        if (request.throwIfError()) {
            throw e;
        }

        return builder
                .error(e)
                .success(false)
                .build();
    }

}
