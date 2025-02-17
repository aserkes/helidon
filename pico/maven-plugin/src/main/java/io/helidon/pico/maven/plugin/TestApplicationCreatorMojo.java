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

package io.helidon.pico.maven.plugin;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static io.helidon.pico.maven.plugin.MavenPluginUtils.picoServices;
import static io.helidon.pico.tools.ApplicationCreatorDefault.APPLICATION_NAME_SUFFIX;
import static io.helidon.pico.tools.ApplicationCreatorDefault.NAME_PREFIX;
import static io.helidon.pico.tools.ApplicationCreatorDefault.upperFirstChar;
import static io.helidon.pico.tools.ModuleUtils.toBasePath;
import static io.helidon.pico.tools.ModuleUtils.toSuggestedModuleName;

/**
 * A mojo wrapper to {@link io.helidon.pico.tools.spi.ApplicationCreator} for test specific types.
 */
@Mojo(name = "test-application-create", defaultPhase = LifecyclePhase.TEST_COMPILE, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.TEST)
@SuppressWarnings("unused")
public class TestApplicationCreatorMojo extends AbstractApplicationCreatorMojo {

    /**
     * The classname to use for the Pico {@link io.helidon.pico.Application} test class.
     * If not found the classname will be inferred.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".application.class.name", readonly = true
               // note: the default value handling doesn't work here for "$$"!!
               //               defaultValue = DefaultApplicationCreator.APPLICATION_NAME
               )
    private String className;

    /**
     * Specify where to place generated source files created by annotation processing.
     * Only applies to JDK 1.6+
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/test-annotations")
    private File generatedTestSourcesDirectory;

    /**
     * The directory where compiled test classes go.
     */
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true, readonly = true)
    private File testOutputDirectory;

    @Override
    File getGeneratedSourceDirectory() {
        return generatedTestSourcesDirectory;
    }

    @Override
    File getOutputDirectory() {
        return testOutputDirectory;
    }

    @Override
    List<Path> getSourceRootPaths() {
        return getTestSourceRootPaths();
    }

    @Override
    LinkedHashSet<Path> getClasspathElements() {
        MavenProject project = getProject();
        LinkedHashSet<Path> result = new LinkedHashSet<>(project.getTestArtifacts().size());
        result.add(new File(project.getBuild().getTestOutputDirectory()).toPath());
        for (Object a : project.getTestArtifacts()) {
            result.add(((Artifact) a).getFile().toPath());
        }
        result.addAll(super.getClasspathElements());
        return result;
    }

    @Override
    LinkedHashSet<Path> getModulepathElements() {
        return getClasspathElements();
    }

    @Override
    String getThisModuleName() {
        Build build = getProject().getBuild();
        Path basePath = toBasePath(build.getTestSourceDirectory());
        String moduleName = toSuggestedModuleName(basePath, Path.of(build.getTestOutputDirectory()), true).orElseThrow();
        return moduleName;
    }

    @Override
    String getGeneratedClassName() {
        return (className == null) ? NAME_PREFIX + "Test" + APPLICATION_NAME_SUFFIX : className;
    }

    @Override
    String getClassPrefixName() {
        return upperFirstChar(ActivatorCreatorCodeGen.DEFAULT_TEST_CLASS_PREFIX_NAME);
    }

    /**
     * Default constructor.
     */
    public TestApplicationCreatorMojo() {
    }

    /**
     * Excludes everything from source main scope.
     */
    @Override
    Set<TypeName> getServiceTypeNamesForExclusion() {
        Set<Path> classPath = getSourceClasspathElements();

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        URLClassLoader loader = ExecutableClassLoader.create(classPath, prev);

        try {
            Thread.currentThread().setContextClassLoader(loader);

            PicoServices picoServices = picoServices(false);
            assert (!picoServices.config().usesCompileTimeApplications());
            Services services = picoServices.services();

            // retrieves all the services in the registry
            List<ServiceProvider<?>> allServices = services
                    .lookupAll(ServiceInfoCriteriaDefault.builder().build(), false);
            Set<TypeName> serviceTypeNames = toNames(allServices);
            getLog().debug("excluding service type names: " + serviceTypeNames);
            return serviceTypeNames;
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

}
