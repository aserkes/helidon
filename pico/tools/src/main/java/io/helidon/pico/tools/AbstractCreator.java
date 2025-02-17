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
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.runtime.AbstractServiceProvider;
import io.helidon.pico.runtime.DefaultServiceBinder;

import static io.helidon.pico.tools.CommonUtils.hasValue;
import static io.helidon.pico.tools.TypeTools.needToDeclareModuleUsage;
import static io.helidon.pico.tools.TypeTools.needToDeclarePackageUsage;

/**
 * Abstract base for any codegen creator.
 */
public abstract class AbstractCreator {
    /**
     * The default java source version (this can be explicitly overridden using the builder or maven plugin).
     */
    public static final String DEFAULT_SOURCE = "11";
    /**
     * The default java target version (this can be explicitly overridden using the builder or maven plugin).
     */
    public static final String DEFAULT_TARGET = "11";

    // no special chars since this will be used as a package and class name
    static final String NAME_PREFIX = "Pico$$";
    static final String PICO_FRAMEWORK_MODULE = PicoServicesConfig.FQN + ".runtime";
    static final String MODULE_NAME_SUFFIX = "Module";

    private final System.Logger logger = System.getLogger(getClass().getName());
    private final TemplateHelper templateHelper;
    private final String templateName;

    AbstractCreator(String templateName) {
        this.templateHelper = TemplateHelper.create();
        this.templateName = templateName;
    }

    System.Logger logger() {
        return logger;
    }

    TemplateHelper templateHelper() {
        return templateHelper;
    }

    String templateName() {
        return templateName;
    }

    /**
     * Creates a codegen filer that is not reliant on annotation processing, but still capable of creating source
     * files and resources.
     *
     * @param paths          the paths for where files should be read or written.
     * @param isAnalysisOnly true if analysis only, where no code or resources will be physically written to disk
     * @return the code gen filer instance to use
     */
    CodeGenFiler createDirectCodeGenFiler(CodeGenPaths paths,
                                          boolean isAnalysisOnly) {
        AbstractFilerMessager filer = AbstractFilerMessager.createDirectFiler(paths, logger);
        return new CodeGenFiler(filer, !isAnalysisOnly);
    }

    /**
     * The generated sticker string.
     *
     * @param req the creator request
     * @return the sticker
     */
    String toGeneratedSticker(GeneralCreatorRequest req) {
        String generator = (null == req) ? null : req.generator().orElse(null);
        return templateHelper.generatedStickerFor((generator != null) ? generator : getClass().getName());
    }

    /**
     * Generates the {@link io.helidon.pico.api.Activator} source code for the provided service providers. Custom
     * service providers (see {@link AbstractServiceProvider#isCustom()}) do not qualify to
     * have activators code generated.
     *
     * @param sp the collection of service providers
     * @return the code generated string for the service provider given
     */
    static String toActivatorCodeGen(ServiceProvider<?> sp) {
        if (sp instanceof AbstractServiceProvider && ((AbstractServiceProvider<?>) sp).isCustom()) {
            return null;
        }
        return DefaultServiceBinder.toRootProvider(sp).activator().orElseThrow().getClass().getName() + ".INSTANCE";
    }

    /**
     * Generates the {@link io.helidon.pico.api.Activator} source code for the provided service providers.
     *
     * @param coll the collection of service providers
     * @return the code generated string for the collection of service providers given
     */
    static String toActivatorCodeGen(Collection<ServiceProvider<?>> coll) {
        return CommonUtils.toString(coll, AbstractCreator::toActivatorCodeGen, null);
    }

    /**
     * Automatically adds the requirements to the module-info descriptor for what pico requires.
     *
     * @param moduleInfo the module info descriptor
     * @param generatedAnno  the generator sticker value
     * @return the modified descriptor, fluent style
     */
    ModuleInfoDescriptorDefault.Builder addPicoProviderRequirementsTo(ModuleInfoDescriptorDefault.Builder moduleInfo,
                                                                      String generatedAnno) {
        Objects.requireNonNull(generatedAnno);
        // requirements on the pico services framework itself
        String preComment = "    // " + PicoServicesConfig.NAME + " services - Generated(" + generatedAnno + ")";
        ModuleInfoDescriptor.addIfAbsent(moduleInfo, PICO_FRAMEWORK_MODULE, ModuleInfoItemDefault.builder()
                .requires(true)
                .target(PICO_FRAMEWORK_MODULE)
                .transitiveUsed(true)
                .addPrecomment(preComment));
        return moduleInfo;
    }

    ModuleInfoDescriptor createModuleInfo(ModuleInfoCreatorRequest req) {
        String generatedAnno = templateHelper.generatedStickerFor(getClass().getName());
        String moduleInfoPath = req.moduleInfoPath().orElse(null);
        String moduleName = req.name().orElse(null);
        TypeName moduleTypeName = req.moduleTypeName();
        TypeName applicationTypeName = req.applicationTypeName().orElse(null);
        String classPrefixName = req.classPrefixName();
        boolean isModuleCreated = req.moduleCreated();
        boolean isApplicationCreated = req.applicationCreated();
        Collection<String> modulesRequired = req.modulesRequired();
        Map<TypeName, Set<TypeName>> contracts = req.contracts();
        Map<TypeName, Set<TypeName>> externalContracts = req.externalContracts();

        ModuleInfoDescriptorDefault.Builder descriptorBuilder;
        if (moduleInfoPath != null) {
            descriptorBuilder = ModuleInfoDescriptorDefault
                    .toBuilder(ModuleInfoDescriptor.create(Paths.get(moduleInfoPath)));
            if (hasValue(moduleName) && ModuleUtils.isUnnamedModuleName(descriptorBuilder.name())) {
                descriptorBuilder.name(moduleName);
            }
            assert (descriptorBuilder.name().equals(moduleName) || (!hasValue(moduleName)))
                    : "bad module name: " + moduleName + " targeting " + descriptorBuilder.name();
            moduleName = descriptorBuilder.name();
        } else {
            descriptorBuilder = ModuleInfoDescriptorDefault.builder().name(moduleName);
            descriptorBuilder.headerComment("// @Generated(" + generatedAnno + ")");
        }

        boolean isTestModule = ModuleInfoDescriptor.DEFAULT_TEST_SUFFIX.equals(classPrefixName);
        if (isTestModule) {
            String baseModuleName = ModuleUtils.normalizedBaseModuleName(moduleName);
            ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, baseModuleName, ModuleInfoItemDefault.builder()
                    .requires(true)
                    .target(baseModuleName)
                    .transitiveUsed(true));
        }

        if (isModuleCreated && (moduleTypeName != null)) {
            if (!isTestModule) {
                ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, moduleTypeName.packageName(),
                                                 ModuleInfoItemDefault.builder()
                                                         .exports(true)
                                                         .target(moduleTypeName.packageName()));
            }
            ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, TypeNames.PICO_MODULE,
                                             ModuleInfoItemDefault.builder()
                                                     .provides(true)
                                                     .target(TypeNames.PICO_MODULE)
                                                     .addWithOrTo(moduleTypeName.name())
                                                     .addPrecomment("    // "
                                                                            + PicoServicesConfig.NAME
                                                                            + " module - Generated("
                                                                            + generatedAnno + ")"));
        }
        if (isApplicationCreated && applicationTypeName != null) {
            if (!isTestModule) {
                ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, applicationTypeName.packageName(),
                                                 ModuleInfoItemDefault.builder()
                                                         .exports(true)
                                                         .target(applicationTypeName.packageName()));
            }
            ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, TypeNames.PICO_APPLICATION,
                                             ModuleInfoItemDefault.builder()
                                                     .provides(true)
                                                     .target(TypeNames.PICO_APPLICATION)
                                                     .addWithOrTo(applicationTypeName.name())
                                                     .addPrecomment("    // "
                                                                            + PicoServicesConfig.NAME
                                                                            + " application - Generated("
                                                                            + generatedAnno + ")"));
        }

        String preComment = "    // " + PicoServicesConfig.NAME + " external contract usage - Generated(" + generatedAnno + ")";
        if (modulesRequired != null) {
            for (String externalModuleName : modulesRequired) {
                if (!needToDeclareModuleUsage(externalModuleName)) {
                    continue;
                }

                ModuleInfoItemDefault.Builder itemBuilder = ModuleInfoItemDefault.builder()
                        .requires(true)
                        .target(externalModuleName);
                if (hasValue(preComment)) {
                    itemBuilder.addPrecomment(preComment);
                }

                boolean added = ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, externalModuleName, itemBuilder);
                if (added) {
                    preComment = "";
                }
            }
        }

        Set<TypeName> allExternalContracts = toAllContracts(externalContracts);
        for (TypeName cn : allExternalContracts) {
            String packageName = cn.packageName();
            if (!needToDeclarePackageUsage(packageName)) {
                continue;
            }

            ModuleInfoItemDefault.Builder itemBuilder = ModuleInfoItemDefault.builder()
                    .uses(true)
                    .target(cn.name());
            if (hasValue(preComment)) {
                itemBuilder.addPrecomment(preComment);
            }

            boolean added = ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, cn.name(), itemBuilder);
            if (added) {
                preComment = "";
            }
        }

        if (!isTestModule && (contracts != null)) {
            preComment = "    // " + PicoServicesConfig.NAME + " contract usage - Generated(" + generatedAnno + ")";
            for (Map.Entry<TypeName, Set<TypeName>> e : contracts.entrySet()) {
                for (TypeName contract : e.getValue()) {
                    if (!allExternalContracts.contains(contract)) {
                        String packageName = contract.packageName();
                        if (!needToDeclarePackageUsage(packageName)) {
                            continue;
                        }

                        ModuleInfoItemDefault.Builder itemBuilder = ModuleInfoItemDefault.builder()
                                .exports(true)
                                .target(packageName);
                        if (hasValue(preComment)) {
                            itemBuilder.addPrecomment(preComment);
                        }

                        boolean added = ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, packageName, itemBuilder);
                        if (added) {
                            preComment = "";
                        }
                    }
                }
            }
        }

        return addPicoProviderRequirementsTo(descriptorBuilder, generatedAnno);
    }

    static Set<TypeName> toAllContracts(Map<TypeName, Set<TypeName>> servicesToContracts) {
        Set<TypeName> result = new LinkedHashSet<>();
        servicesToContracts.forEach((serviceTypeName, cn) -> result.addAll(cn));
        return result;
    }

    /**
     * Creates the {@link io.helidon.pico.tools.CodeGenPaths} given the current batch of services to process.
     *
     * @param servicesToProcess the services to process
     * @return the payload for code gen paths
     */
    static CodeGenPaths createCodeGenPaths(ServicesToProcess servicesToProcess) {
        Path moduleInfoFilePath = servicesToProcess.lastGeneratedModuleInfoFilePath();
        if (moduleInfoFilePath == null) {
            moduleInfoFilePath = servicesToProcess.lastKnownModuleInfoFilePath();
        }
        return CodeGenPathsDefault.builder()
                .moduleInfoPath(Optional.ofNullable((moduleInfoFilePath != null) ? moduleInfoFilePath.toString() : null))
                .build();
    }

}
