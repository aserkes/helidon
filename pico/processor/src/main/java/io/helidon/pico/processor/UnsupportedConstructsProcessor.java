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

package io.helidon.pico.processor;

import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.TypeNames;

/**
 * When used will recognize constructs that are explicitly known to be unsupported in Pico's reference implementation.
 * Examples include:
 * <ul>
 *  <li>{@link jakarta.annotation.ManagedBean} and "javax.annotation.ManagedBean"
 *  <li>{@link jakarta.annotation.Resource} and "javax...."
 *  <li>{@link jakarta.annotation.Resources} and "javax...."
 *  <li>Any scopes from jakarta.enterprise api module(s) other than ApplicationScoped, which can optionally be mapped to
 *      Singleton scope.
 * </ul>
 */
public class UnsupportedConstructsProcessor extends AbstractProcessor {
    private static final System.Logger LOGGER = System.getLogger(UnsupportedConstructsProcessor.class.getName());

    private static final Set<String> UNSUPPORTED_TARGETS = Set.of(
            TypeNames.JAKARTA_MANAGED_BEAN,
            TypeNames.JAKARTA_RESOURCE,
            TypeNames.JAKARTA_RESOURCES,
            TypeNames.JAKARTA_APPLICATION_SCOPED,
            TypeNames.JAVAX_APPLICATION_SCOPED,
            TypeNames.JAKARTA_CDI_BEFORE_DESTROYED,
            TypeNames.JAKARTA_CDI_CONVERSATION_SCOPED,
            TypeNames.JAKARTA_CDI_DEPENDENT,
            TypeNames.JAKARTA_CDI_DESTROYED,
            TypeNames.JAKARTA_CDI_INITIALIZED,
            TypeNames.JAKARTA_CDI_NORMAL_SCOPE,
            TypeNames.JAKARTA_CDI_REQUEST_SCOPED,
            TypeNames.JAKARTA_CDI_SESSION_SCOPED,
            TypeNames.JAKARTA_CDI_ACTIVATE_REQUEST_CONTEXT,
            TypeNames.JAKARTA_CDI_OBSERVES,
            TypeNames.JAKARTA_CDI_OBSERVES_ASYNC,
            TypeNames.JAKARTA_CDI_ALTERNATIVE,
            TypeNames.JAKARTA_CDI_DISPOSES,
            TypeNames.JAKARTA_CDI_INTERCEPTED,
            TypeNames.JAKARTA_CDI_MODEL,
            TypeNames.JAKARTA_CDI_PRODUCES,
            TypeNames.JAKARTA_CDI_SPECIALIZES,
            TypeNames.JAKARTA_CDI_STEREOTYPE,
            TypeNames.JAKARTA_CDI_TRANSIENT_REFERENCE,
            TypeNames.JAKARTA_CDI_TYPED,
            TypeNames.JAKARTA_CDI_VETOED,
            TypeNames.JAKARTA_CDI_NONBINDING);

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public UnsupportedConstructsProcessor() {
    }

    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return UNSUPPORTED_TARGETS;
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        Options.init(processingEnv);
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!annotations.isEmpty()) {
            Set<String> annotationTypeNames = annotations.stream().map(Object::toString).collect(Collectors.toSet());
            if (Options.isOptionEnabled(Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE)) {
                annotationTypeNames.remove(TypeNames.JAKARTA_APPLICATION_SCOPED);
                annotationTypeNames.remove(TypeNames.JAVAX_APPLICATION_SCOPED);
            }

            if (!annotationTypeNames.isEmpty()) {
                if (Options.isOptionEnabled(Options.TAG_IGNORE_UNSUPPORTED_ANNOTATIONS)) {
                    String msg = "ignoring unsupported annotations: " + annotationTypeNames;
                    LOGGER.log(System.Logger.Level.DEBUG, msg);
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg);
                    return false;
                }

                String msg = "This module contains unsupported annotations for " + PicoServicesConfig.NAME
                                + " to process: " + annotationTypeNames + ".\n";
                if (annotationTypeNames.contains(TypeNames.JAKARTA_APPLICATION_SCOPED)
                        || annotationTypeNames.contains(TypeNames.JAVAX_APPLICATION_SCOPED)) {
                    msg += "'" + TypeNames.JAKARTA_APPLICATION_SCOPED + "' can be optionally mapped to '"
                            + TypeNames.JAKARTA_SINGLETON
                                + "' scope by passing -A" + Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE + "=true.\n";
                }
                msg += "Use -A" + Options.TAG_IGNORE_UNSUPPORTED_ANNOTATIONS + "=true to ignore all unsupported annotations.";

                LOGGER.log(System.Logger.Level.ERROR, msg);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            }
        }

        return false;
    }

}
