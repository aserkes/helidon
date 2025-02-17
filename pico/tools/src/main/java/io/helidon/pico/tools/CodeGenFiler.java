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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;
import io.helidon.pico.api.PicoServicesConfig;

import static io.helidon.pico.tools.ModuleUtils.PICO_MODULE_INFO_JAVA_NAME;
import static io.helidon.pico.tools.ModuleUtils.normalizedBaseModuleName;
import static io.helidon.pico.tools.ModuleUtils.saveAppPackageName;
import static io.helidon.pico.tools.ModuleUtils.toPath;

/**
 * This class is used to generate the source and resources originating from either annotation processing or maven-plugin
 * invocation. It also provides a circuit breaker in case the filer should be disabled from actually writing out source
 * and resources, and instead will use the filer's messager to report what it would have performed (applicable for apt cases).
 */
public class CodeGenFiler {
    private static final boolean FORCE_MODULE_INFO_PICO_INTO_SCRATCH_DIR = true;
    private static final boolean FILER_WRITE_ONCE_PER_TYPE = true;
    private static final Set<TypeName> FILER_TYPES_FILED = new LinkedHashSet<>();
    private static boolean filerWriteIsDisabled;

    private final AbstractFilerMessager filer;
    private final Boolean enabled;
    private final Map<Path, Path> deferredMoves = new LinkedHashMap<>();
    private Path targetOutputPath;
    private String scratchPathName;

    /**
     * Constructor.
     *
     * @param filer the filer to use for creating resources
     */
    CodeGenFiler(AbstractFilerMessager filer) {
        this(filer, null);
    }

    /**
     * Constructor.
     *
     * @param filer the filer to use for creating resources
     * @param enabled true if forcing enablement, false if forcing disablement, null for using defaults
     */
    CodeGenFiler(AbstractFilerMessager filer,
                 Boolean enabled) {
        this.filer = Objects.requireNonNull(filer);
        this.enabled = enabled;
    }

    /**
     * Creates a new code gen filer.
     *
     * @param filer the physical filer
     * @return a newly created code gen filer
     */
    public static CodeGenFiler create(AbstractFilerMessager filer) {
        return new CodeGenFiler(filer);
    }

    /**
     * Provides the ability to disable actual file writing (convenient for unit testing). The default is true for
     * enabled.
     *
     * @param enabled if disabled, pass false
     * @return the previous value of this setting
     */
    static boolean filerEnabled(boolean enabled) {
        boolean prev = filerWriteIsDisabled;
        filerWriteIsDisabled = enabled;
        return prev;
    }

    boolean isFilerWriteEnabled() {
        return (enabled != null) ? enabled : !filerWriteIsDisabled;
    }

    AbstractFilerMessager filer() {
        return filer;
    }

    Messager messager() {
        return filer;
    }

    /**
     * This map represents any move operations that were not capable at the time of code generation, that must be deferred
     * until after the annotation processor has completed its round.
     *
     * @return map of deferred moves from source to target path locations
     */
    public Map<Path, Path> deferredMoves() {
        return Map.copyOf(deferredMoves);
    }

    /**
     * Generate the meta-inf services given the provided map.
     *
     * @param paths           paths to where code should be written
     * @param metaInfServices the meta-inf services mapping
     */
    public void codegenMetaInfServices(CodeGenPaths paths,
                                       Map<String, List<String>> metaInfServices) {
        if (metaInfServices == null || metaInfServices.isEmpty()) {
            return;
        }

        Filer filer = filer();
        Messager messager = messager();
        Map<String, Set<String>> mergedMap = new LinkedHashMap<>();
        // load up any existing values, since this compilation may be partial and be run again...
        for (Map.Entry<String, List<String>> e : metaInfServices.entrySet()) {
            String contract = e.getKey();
            Set<String> mergedSet = new LinkedHashSet<>(e.getValue());
            mergedMap.put(contract, mergedSet);
            String outPath = new File(paths.metaInfServicesPath()
                                              .orElse(CodeGenPaths.DEFAULT_META_INF_SERVICES_PATH), contract).getPath();
            try {
                messager.debug("Reading " + outPath);
                FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", outPath);
                try (InputStream is = f.openInputStream();
                        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        mergedSet.add(line);
                    }
                }
                targetOutputPath(f);
            } catch (FilerException | NoSuchFileException x) {
                // don't show the exception in this case
                messager.debug(getClass().getSimpleName() + ":" + x.getMessage());
            } catch (Exception x) {
                ToolsException te =
                        new ToolsException("Failed to find/load existing META-INF/services file: " + x.getMessage(), x);
                messager.warn(te.getMessage(), te);
            }
        }

        for (Map.Entry<String, Set<String>> e : mergedMap.entrySet()) {
            String contract = e.getKey();
            String outPath = new File(paths.metaInfServicesPath().orElseThrow(), contract).getPath();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
                for (String value : e.getValue()) {
                    pw.println(value);
                }
            }

            codegenResourceFilerOut(outPath, baos.toString(StandardCharsets.UTF_8), Optional.empty());
        }
    }

    private void targetOutputPath(FileObject f) {
        Path path = Path.of(f.toUri());
        Path parent = path.getParent();
        Path gparent = (parent == null) ? null : parent.getParent();
        this.targetOutputPath = gparent;
        Path scratchName = (parent == null) ? null : parent.getFileName();
        this.scratchPathName = (scratchName == null) ? null : scratchName.toString();
    }

    private Path toScratchPath(boolean wantClassesOrTestClassesRelative) {
        Objects.requireNonNull(targetOutputPath);
        Objects.requireNonNull(scratchPathName);
        Path base = targetOutputPath.resolve(PicoServicesConfig.NAME);
        return (wantClassesOrTestClassesRelative) ? base.resolve(scratchPathName) : base;
    }

    /**
     * Code generates a resource, providing the ability to update if the resource already exists.
     *
     * @param outPath   the path to output the resource to
     * @param body      the resource body
     * @param optFnUpdater the optional updater of the body
     * @return file path coordinates corresponding to the resource in question, or empty if not generated
     */
    public Optional<Path> codegenResourceFilerOut(String outPath,
                                                  String body,
                                                  Optional<Function<InputStream, String>> optFnUpdater) {
        Messager messager = messager();
        if (!isFilerWriteEnabled()) {
            messager.log("(disabled) Writing " + outPath + " with:\n" + body);
            return Optional.empty();
        }
        messager.debug("Writing " + outPath);

        Filer filer = filer();
        boolean contentsAlreadyVerified = false;
        Function<InputStream, String> fnUpdater = optFnUpdater.orElse(null);
        AtomicReference<File> fileRef = new AtomicReference<>();
        try {
            if (fnUpdater != null) {
                // attempt to update it...
                try {
                    FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", outPath);
                    try (InputStream is = f.openInputStream()) {
                        String newBody = fnUpdater.apply(is);
                        if (newBody != null) {
                            body = newBody;
                        }
                    }
                } catch (NoSuchFileException e) {
                    // no-op
                } catch (Exception e) {
                    // messager.debug(getClass().getSimpleName() + ":" + e.getMessage());
                    contentsAlreadyVerified = tryToEnsureSameContents(e, body, messager, fileRef);
                }
            }

            // write it
            FileObject f = filer.createResource(StandardLocation.CLASS_OUTPUT, "", outPath);
            try (Writer os = f.openWriter()) {
                os.write(body);
            }
            targetOutputPath(f);

            if (FORCE_MODULE_INFO_PICO_INTO_SCRATCH_DIR && outPath.equals(PICO_MODULE_INFO_JAVA_NAME)
                    && targetOutputPath != null) {
                // hack: physically relocate it elsewhere
                Path originalPath = Path.of(f.toUri());
                Path newPath = toScratchPath(true).resolve(PICO_MODULE_INFO_JAVA_NAME);
                if (originalPath.toFile().exists()) {
                    Path parent = newPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.move(originalPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                    return Optional.of(newPath);
                } else {
                    deferredMoves.put(originalPath, newPath);
                }
            }

            return toPath(f.toUri());
        } catch (FilerException x) {
            // messager.debug(getClass().getSimpleName() + ":" + x.getMessage(), null);
            if (!contentsAlreadyVerified) {
                tryToEnsureSameContents(x, body, messager, fileRef);
            }
        } catch (Exception x) {
            ToolsException te = new ToolsException("Failed to write resource file: " + x.getMessage(), x);
            messager.error(te.getMessage(), te);
        }

        return Optional.of(fileRef.get().toPath());
    }

    /**
     * Throws an error if the contents being written cannot be written, and the desired content is different from what
     * is on disk.
     *
     * @param e        the exception thrown by the filer
     * @param expected the expected body of the resource
     * @param messager the messager to handle errors and logging
     * @param fileRef  the reference that will be set to the coordinates of the resource
     * @return true if the implementation was able to verify the contents match
     */
    boolean tryToEnsureSameContents(Exception e,
                                    String expected,
                                    Messager messager,
                                    AtomicReference<File> fileRef) {
        if (!(e instanceof FilerException)) {
            return false;
        }

        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        int pos = message.lastIndexOf(' ');
        if (pos <= 0) {
            return false;
        }

        String maybePath = message.substring(pos + 1);
        File file = new File(maybePath);
        if (!file.exists()) {
            return false;
        }
        if (fileRef != null) {
            fileRef.set(file);
        }

        try {
            String actual = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!actual.equals(expected)) {
                String error = "expected contents to match for file: " + file
                        + "\nexpected:\n" + expected
                        + "\nactual:\n" + actual;
                ToolsException te = new ToolsException(error);
                messager.error(error, te);
            }
        } catch (Exception x) {
            messager.debug(getClass().getSimpleName() + ": unable to verify contents match: " + file + "; " + x.getMessage(),
                           null);
            return false;
        }

        return true;
    }

    /**
     * Code generates the {@link io.helidon.pico.api.ModuleComponent} source.
     *
     * @param moduleDetail the module details
     */
    void codegenModuleFilerOut(ModuleDetail moduleDetail) {
        if (moduleDetail.moduleBody().isEmpty()) {
            return;
        }

        TypeName typeName = moduleDetail.moduleTypeName();
        String body = moduleDetail.moduleBody().orElseThrow();
        codegenJavaFilerOut(typeName, body);
    }

    /**
     * Code generates the {@link io.helidon.pico.api.Application} source.
     *
     * @param applicationTypeName the application type
     * @param body                the application body of source
     */
    void codegenApplicationFilerOut(TypeName applicationTypeName,
                                    String body) {
        codegenJavaFilerOut(applicationTypeName, body);
    }

    /**
     * Code generates the {@link io.helidon.pico.api.Activator} source.
     *
     * @param activatorDetail the activator details
     */
    void codegenActivatorFilerOut(ActivatorCodeGenDetail activatorDetail) {
        if (activatorDetail.body().isEmpty()) {
            return;
        }

        TypeName typeName = activatorDetail.serviceTypeName();
        String body = activatorDetail.body().orElseThrow();
        codegenJavaFilerOut(typeName, body);
    }

    /**
     * Code generate a java source file.
     *
     * @param typeName the source type name
     * @param body     the source body
     * @return the new file path coordinates or empty if nothing was written
     */
    public Optional<Path> codegenJavaFilerOut(TypeName typeName,
                                              String body) {
        Messager messager = messager();
        if (!isFilerWriteEnabled()) {
            messager.log("(disabled) Writing " + typeName + " with:\n" + body);
            return Optional.empty();
        }

        if (FILER_WRITE_ONCE_PER_TYPE && !FILER_TYPES_FILED.add(typeName)) {
            messager.log(typeName + ": already processed");
            return Optional.empty();
        }

        messager.debug("Writing " + typeName);

        Filer filer = filer();
        try {
            JavaFileObject javaSrc = filer.createSourceFile(typeName.name());
            try (Writer os = javaSrc.openWriter()) {
                os.write(body);
            }
            return toPath(javaSrc.toUri());
        } catch (FilerException x) {
            messager.log("Failed to write java file: " + x);
        } catch (Exception x) {
            messager.warn("Failed to write java file: " + x, x);
        }

        return Optional.empty();
    }

    /**
     * Code generate the module-info.java.pico file.
     *
     * @param newDeltaDescriptor      the descriptor
     * @param overwriteTargetIfExists should the file be overwritten if it already exists
     * @return the module-info coordinates, or empty if nothing was written
     */
    Optional<Path> codegenModuleInfoFilerOut(ModuleInfoDescriptor newDeltaDescriptor,
                                             boolean overwriteTargetIfExists) {
        Objects.requireNonNull(newDeltaDescriptor);

        Messager messager = messager();
        String typeName = PICO_MODULE_INFO_JAVA_NAME;
        if (!isFilerWriteEnabled()) {
            messager.log("(disabled) Writing " + typeName + " with:\n" + newDeltaDescriptor);
            return Optional.empty();
        }
        messager.debug("Writing " + typeName);

        Function<InputStream, String> moduleInfoUpdater = inputStream -> {
            ModuleInfoDescriptor existingDescriptor = ModuleInfoDescriptor.create(inputStream);
            ModuleInfoDescriptor newDescriptor = existingDescriptor.mergeCreate(newDeltaDescriptor);
            return newDescriptor.contents();
        };

        Optional<Path> filePath
                = codegenResourceFilerOut(typeName, newDeltaDescriptor.contents(), Optional.of(moduleInfoUpdater));
        if (filePath.isPresent()) {
            messager.debug("Wrote module-info: " + filePath.get());
        } else if (overwriteTargetIfExists) {
            messager.warn("Expected to have written module-info, but failed to write it");
        }

        if (!newDeltaDescriptor.isUnnamed()) {
            saveAppPackageName(toScratchPath(false),
                                           normalizedBaseModuleName(newDeltaDescriptor.name()));
        }

        return filePath;
    }

    /**
     * Reads in the module-info if it exists, or returns null if it doesn't exist.
     *
     * @param name the name to the module-info file
     * @return the module-info descriptor, or empty if it doesn't exist
     */
    Optional<ModuleInfoDescriptor> readModuleInfo(String name) {
        Objects.requireNonNull(name);

        try {
            CharSequence body = readResourceAsString(name);
            return Optional.ofNullable((body == null) ? null : ModuleInfoDescriptor.create(body.toString()));
        } catch (Exception e) {
            throw new ToolsException("Failed to read module-info: " + name, e);
        }
    }

    /**
     * Reads in a resource from the {@link javax.tools.StandardLocation#CLASS_OUTPUT} location.
     *
     * @param name the name of the resource
     * @return the body of the resource as a string, or null if it doesn't exist
     */
    CharSequence readResourceAsString(String name) {
        try {
            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", name);
            targetOutputPath(f);
            return f.getCharContent(true);
        } catch (IOException e) {
            if (FORCE_MODULE_INFO_PICO_INTO_SCRATCH_DIR && name.equals(PICO_MODULE_INFO_JAVA_NAME)
                    && targetOutputPath != null) {
                // hack: physically read it from its relocated location
                File newPath = new File(targetOutputPath.toFile().getAbsolutePath(), name);
                if (newPath.exists()) {
                    try {
                        return Files.readString(newPath.toPath());
                    } catch (IOException e2) {
                        throw new ToolsException(e2.getMessage(), e2);
                    }
                }
            }

            messager().debug("unable to load resource: " + name);
            return null;
        }
    }

    /**
     * Attempts to translate the resource name to a file coordinate, or null if translation is not possible.
     *
     * @param name the name of the resource
     * @return the file path coordinates if it can be ascertained, or empty if not possible to ascertain this information
     */
    Optional<Path> toResourceLocation(String name) {
        try {
            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", name);
            targetOutputPath(f);
            return toPath(f.toUri());
        } catch (IOException e) {
            messager().debug("unable to load resource: " + name);
        }
        return Optional.empty();
    }

    /**
     * Attempts to translate the type name to a file coordinate, or empty if translation is not possible.
     *
     * @param name the name of the type
     * @return the file coordinates if it can be ascertained, or empty if not possible to ascertain this information
     *
     * @see ModuleUtils#toSourcePath for annotation processing use cases
     */
    Optional<Path> toSourceLocation(String name) {
        if (filer instanceof AbstractFilerMessager.DirectFilerMessager) {
            TypeName typeName = TypeNameDefault.createFromTypeName(name);
            Optional<Path> path = Optional.ofNullable(((AbstractFilerMessager.DirectFilerMessager) filer)
                                               .toSourcePath(StandardLocation.SOURCE_PATH, typeName));
            if (path.isPresent()) {
                return path;
            }
        }

        messager().warn(CodeGenFiler.class.getSimpleName() + ": unable to determine source location for: " + name);
        return Optional.empty();
    }

}
