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

package io.helidon.pico.tests.pico.tbox;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.pico.api.ActivationResult;
import io.helidon.pico.api.ModuleComponent;
import io.helidon.pico.api.PicoException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.RunLevel;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;
import io.helidon.pico.testing.PicoTestingSupport;
import io.helidon.pico.tests.pico.ASerialProviderImpl;
import io.helidon.pico.tests.pico.TestingSingleton;
import io.helidon.pico.tests.pico.provider.FakeConfig;
import io.helidon.pico.tests.pico.provider.FakeServer;
import io.helidon.pico.tests.pico.stacking.Intercepted;
import io.helidon.pico.tests.pico.tbox.impl.BigHammer;
import io.helidon.pico.tests.pico.tbox.impl.MainToolBox;

import jakarta.inject.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.testing.PicoTestingSupport.resetAll;
import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static io.helidon.pico.tests.pico.TestUtils.loadStringFromFile;
import static io.helidon.pico.tests.pico.TestUtils.loadStringFromResource;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Expectation here is that the annotation processor ran, and we can use standard injection and pico-di registry services, etc.
 */
class ToolBoxTest {

    Config config = PicoTestingSupport.basicTestableConfig();
    PicoServices picoServices;
    Services services;

    @BeforeEach
    void setUp() {
        setUp(config);
    }

    void setUp(Config config) {
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    @AfterEach
    void tearDown() {
        resetAll();
    }

    @Test
    void sanity() {
        assertNotNull(picoServices);
        assertNotNull(services);
    }

    @Test
    void toolbox() {
        List<ServiceProvider<Awl>> blanks = services.lookupAll(Awl.class);
        List<String> desc = blanks.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here
        assertThat(desc,
                   contains("AwlImpl:INIT"));

        List<ServiceProvider<ToolBox>> allToolBoxes = services.lookupAll(ToolBox.class);
        desc = allToolBoxes.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc,
                   contains("MainToolBox:INIT"));

        ToolBox toolBox = allToolBoxes.get(0).get();
        assertThat(toolBox.getClass(), equalTo(MainToolBox.class));
        MainToolBox mtb = (MainToolBox) toolBox;
        assertThat(mtb.postConstructCallCount, equalTo(1));
        assertThat(mtb.preDestroyCallCount, equalTo(0));
        assertThat(mtb.setterCallCount, equalTo(1));
        List<Provider<Tool>> allTools = mtb.toolsInBox();
        desc = allTools.stream().map(Object::toString).collect(Collectors.toList());
        assertThat(desc,
                   contains("SledgeHammer:INIT",
                            "BigHammer:INIT",
                            "TableSaw:INIT",
                            "AwlImpl:INIT",
                            "HandSaw:INIT",
                            "LittleHammer:INIT",
                            "Screwdriver:ACTIVE"));
        assertThat(mtb.screwdriver(), notNullValue());

        Provider<Hammer> hammer = Objects.requireNonNull(toolBox.preferredHammer());
        assertThat(hammer.get(), notNullValue());
        assertThat(hammer.get(), is(hammer.get()));
        assertThat(BigHammer.class, equalTo(hammer.get().getClass()));
        desc = allTools.stream().map(Object::toString).collect(Collectors.toList());
        assertThat(desc,
                   contains("SledgeHammer:INIT",
                            "BigHammer:ACTIVE",
                            "TableSaw:INIT",
                            "AwlImpl:INIT",
                            "HandSaw:INIT",
                            "LittleHammer:INIT",
                            "Screwdriver:ACTIVE"));

        desc = (((MainToolBox) toolBox).allHammers()).stream().map(Object::toString).collect(Collectors.toList());
        assertThat(desc,
                   contains("SledgeHammer:INIT",
                            "BigHammer:ACTIVE",
                            "LittleHammer:INIT"));
        assertThat(((ServiceProvider<?>) ((MainToolBox) toolBox).bigHammer()).description(),
                equalTo("BigHammer:ACTIVE"));
    }

    @Test
    void testClasses() {
        assertThat(services.lookupFirst(TestingSingleton.class),
                   notNullValue());
    }

    /**
     * This assumes {@link io.helidon.pico.tools.Options#TAG_AUTO_ADD_NON_CONTRACT_INTERFACES} has
     * been enabled - see pom.xml
     */
    @Test
    void autoExternalContracts() {
        List<ServiceProvider<Serializable>> allSerializable = services.lookupAll(Serializable.class);
        List<String> desc = allSerializable.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here
        assertThat(desc,
                contains("ASerialProviderImpl:INIT", "Screwdriver:INIT"));
    }

    @Test
    void providerTest() {
        Serializable s1 = services.lookupFirst(Serializable.class).get();
        assertThat(s1, notNullValue());
        assertThat(ASerialProviderImpl.class + " is a higher weight and should have been returned for " + String.class,
                   String.class, equalTo(s1.getClass()));
        assertThat(services.lookupFirst(Serializable.class).get(), not(s1));
    }

    @Test
    void modules() {
        List<ServiceProvider<ModuleComponent>> allModules = services.lookupAll(ModuleComponent.class);
        List<String> desc = allModules.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here
        assertThat("ensure that Annotation Processors are enabled in the tools module meta-inf/services",
                   desc, contains("Pico$$Module:ACTIVE", "Pico$$TestModule:ACTIVE"));
        List<String> names = allModules.stream()
                .sorted()
                .map(m -> m.get().named().orElse(m.get().getClass().getSimpleName() + ":null")).collect(Collectors.toList());
        assertThat(names,
                   contains("io.helidon.pico.tests.pico", "io.helidon.pico.tests.pico/test"));
    }

    /**
     * The pico module-info that was created (by APT processing).
     */
    @Test
    void moduleInfo() {
        assertThat(loadStringFromFile("target/pico/classes/module-info.java.pico"),
                   equalTo(loadStringFromResource("expected/module-info.java._pico_")));
    }

    /**
     * The pico test version of module-info that was created (by APT processing).
     */
    @Test
    void testModuleInfo() {
        assertThat(loadStringFromFile("target/pico/test-classes/module-info.java.pico"),
                   equalTo(loadStringFromResource("expected/tests-module-info.java._pico_")));
    }

    @Test
    void innerClassesCanBeGenerated() {
        FakeServer.Builder s1 = services.lookupFirst(FakeServer.Builder.class).get();
        assertThat(s1, notNullValue());
        assertThat(services.lookupFirst(FakeServer.Builder.class).get(), is(s1));

        FakeConfig.Builder c1 = services.lookupFirst(FakeConfig.Builder.class).get();
        assertThat(c1, notNullValue());
        assertThat(services.lookupFirst(FakeConfig.Builder.class).get(), is(c1));
    }

    /**
     * Targets {@link io.helidon.pico.tests.pico.tbox.AbstractSaw} with derived classes of
     * {@link io.helidon.pico.tests.pico.tbox.impl.HandSaw} and {@link io.helidon.pico.tests.pico.tbox.TableSaw} found in different packages.
     */
    @Test
    void hierarchyOfInjections() {
        List<ServiceProvider<AbstractSaw>> saws = services.lookupAll(AbstractSaw.class);
        List<String> desc = saws.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here
        assertThat(desc,
                   contains("TableSaw:INIT", "HandSaw:INIT"));
        for (ServiceProvider<AbstractSaw> saw : saws) {
            saw.get().verifyState();
        }
    }

    /**
     * This tests the presence of module(s) + application(s) to handle all bindings, with effectively no lookups.
     */
    @Test
    void runlevel() {
        assertThat("we start with 2 because we are looking for interceptors (which there is 2 here in this module)",
                   picoServices.metrics().orElseThrow().lookupCount().orElseThrow(),
                   equalTo(2));
        List<ServiceProvider<?>> runLevelServices = services
                .lookupAll(ServiceInfoCriteriaDefault.builder().runLevel(RunLevel.STARTUP).build(), true);
        List<String> desc = runLevelServices.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc,
                   contains("TestingSingleton:INIT"));

        runLevelServices.forEach(sp -> Objects.requireNonNull(sp.get(), sp + " failed on get()"));
        assertThat("activation should not triggered one new lookup from startup",
                   picoServices.metrics().orElseThrow().lookupCount().orElseThrow(),
                   equalTo(3));
        desc = runLevelServices.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc,
                   contains("TestingSingleton:ACTIVE"));
    }

    /**
     * This assumes the presence of module(s) + application(s) to handle all bindings, with effectively no lookups!
     */
    @Test
    void noServiceActivationRequiresLookupWhenApplicationIsPresent() {
        List<ServiceProvider<?>> allServices = services
                .lookupAll(ServiceInfoCriteriaDefault.builder().build(), true);
        allServices.stream()
                .filter(sp -> !(sp instanceof Provider))
                .forEach(sp -> {
                    sp.get();
                    assertThat("activation should not have triggered any lookups (for singletons): "
                                       + sp + " triggered lookups", picoServices.metrics().orElseThrow().lookupCount(),
                               equalTo(1));
        });
    }

    @Test
    void startupAndShutdownCallsPostConstructAndPreDestroy() {
        assertThat(TestingSingleton.postConstructCount(), equalTo(0));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(0));

        List<ServiceProvider<Intercepted>> allInterceptedBefore = services.lookupAll(Intercepted.class);
        assertThat(allInterceptedBefore.size(), greaterThan(0));
        assertThat(TestingSingleton.postConstructCount(), equalTo(0));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(0));

        TestingSingleton testingSingletonFromLookup = picoServices.services().lookup(TestingSingleton.class).get();
        assertThat(testingSingletonFromLookup, notNullValue());
        assertThat(TestingSingleton.postConstructCount(), equalTo(1));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(0));

        Map<String, ActivationResult> map = picoServices.shutdown().orElseThrow();
        Map<String, String> report = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          e -> e.getValue().startingActivationPhase().toString()
                                                  + "->" + e.getValue().finishingActivationPhase()));
        assertThat(report, hasEntry("io.helidon.pico.tests.pico.Pico$$Application", "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry("io.helidon.pico.tests.pico.Pico$$Module", "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry("io.helidon.pico.tests.pico.Pico$$TestApplication", "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry("io.helidon.pico.tests.pico.Pico$$TestModule", "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry("io.helidon.pico.tests.pico.stacking.MostOuterInterceptedImpl", "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry("io.helidon.pico.tests.pico.stacking.OuterInterceptedImpl", "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry("io.helidon.pico.tests.pico.stacking.InterceptedImpl", "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry("io.helidon.pico.tests.pico.TestingSingleton", "ACTIVE->DESTROYED"));
        assertThat(report + " : expected 8 services to be present", report.size(), equalTo(8));

        assertThat(TestingSingleton.postConstructCount(), equalTo(1));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(1));

        assertThat(picoServices.metrics().orElseThrow().lookupCount().orElse(0), equalTo(0));

        PicoException e = assertThrows(PicoException.class, () -> picoServices.services());
        assertThat(e.getMessage(), equalTo("Must reset() after shutdown()"));

        tearDown();
        setUp();
        TestingSingleton testingSingletonFromLookup2 = picoServices.services().lookup(TestingSingleton.class).get();
        assertThat(testingSingletonFromLookup2, not(testingSingletonFromLookup));

        map = picoServices.shutdown().orElseThrow();
        report = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          e2 -> e2.getValue().startingActivationPhase().toString()
                                                  + "->" + e2.getValue().finishingActivationPhase()));
        assertThat(report.toString(), report.size(), is(8));

        tearDown();
        map = picoServices.shutdown().orElseThrow();
        assertThat(map.toString(), map.size(), is(0));
    }

    @Test
    void knownProviders() {
        List<ServiceProvider<?>> providers = services.lookupAll(
                ServiceInfoCriteriaDefault.builder().addContractImplemented(Provider.class.getName()).build());
        List<String> desc = providers.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here (weight ranked)
        assertThat(desc,
                contains("ASerialProviderImpl:INIT",
                         "MyServices$MyConcreteClassContractPerRequestIPProvider:INIT",
                         "MyServices$MyConcreteClassContractPerRequestProvider:INIT",
                         "BladeProvider:INIT"));
    }

}
