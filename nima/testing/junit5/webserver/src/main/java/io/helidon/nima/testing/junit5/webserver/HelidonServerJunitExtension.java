/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.testing.junit5.webserver;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.testing.junit5.webserver.spi.ServerJunitExtension;
import io.helidon.nima.webserver.ListenerConfiguration;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.WebServer;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import static io.helidon.nima.testing.junit5.webserver.Junit5Util.withStaticMethods;
import static io.helidon.nima.webserver.WebServer.DEFAULT_SOCKET_NAME;

/**
 * JUnit5 extension to support Helidon Níma WebServer in tests.
 */
class HelidonServerJunitExtension implements BeforeAllCallback,
                                             AfterAllCallback,
                                             AfterEachCallback,
                                             InvocationInterceptor,
                                             ParameterResolver {

    private final Map<String, URI> uris = new ConcurrentHashMap<>();
    private final List<ServerJunitExtension> extensions;

    private Class<?> testClass;
    private WebServer server;

    HelidonServerJunitExtension() {
        this.extensions = HelidonServiceLoader.create(ServiceLoader.load(ServerJunitExtension.class)).asList();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        LogConfig.configureRuntime();

        testClass = context.getRequiredTestClass();
        ServerTest testAnnot = testClass.getAnnotation(ServerTest.class);
        if (testAnnot == null) {
            throw new IllegalStateException("Invalid test class for this extension: " + testClass);
        }

        WebServer.Builder builder = WebServer.builder()
                .port(0)
                .shutdownHook(false)
                .host("localhost");

        extensions.forEach(it -> it.beforeAll(context));
        extensions.forEach(it -> it.updateServerBuilder(builder));

        setupServer(builder);
        addRouting(builder);

        server = builder.start();
        uris.put(DEFAULT_SOCKET_NAME, URI.create("http://localhost:" + server.port() + "/"));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        extensions.forEach(it -> it.afterAll(extensionContext));

        if (server != null) {
            server.stop();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        extensions.forEach(it -> it.afterEach(extensionContext));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.equals(WebServer.class)) {
            return true;
        }
        if (paramType.equals(URI.class)) {
            return true;
        }

        for (ServerJunitExtension extension : extensions) {
            if (extension.supportsParameter(parameterContext, extensionContext)) {
                return true;
            }
        }

        Context context;
        if (server == null) {
            context = Contexts.globalContext();
        } else {
            context = server.context();
        }
        return context.get(paramType).isPresent();
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.equals(WebServer.class)) {
            return server;
        }
        if (paramType.equals(URI.class)) {
            return uri(parameterContext.getDeclaringExecutable(), Junit5Util.socketName(parameterContext.getParameter()));
        }

        for (ServerJunitExtension extension : extensions) {
            if (extension.supportsParameter(parameterContext, extensionContext)) {
                return extension.resolveParameter(parameterContext, extensionContext, paramType, server);
            }
        }

        Context context;
        if (server == null) {
            context = Contexts.globalContext();
        } else {
            context = server.context();
        }

        return context.get(paramType)
                .orElseThrow(() -> new ParameterResolutionException("Failed to resolve parameter of type "
                                                                            + paramType.getName()));
    }

    private URI uri(Executable declaringExecutable, String socketName) {
        URI uri = uris.computeIfAbsent(socketName, it -> {
            int port = server.port(it);
            if (port == -1) {
                return null;
            }
            return URI.create("http://localhost:" + port + "/");
        });

        if (uri == null) {
            throw new IllegalStateException(declaringExecutable + " expects injection of URI parameter for socket named "
                                                    + socketName
                                                    + ", which is not available on the running webserver");
        }
        return uri;
    }

    private void setupServer(WebServer.Builder builder) {
        withStaticMethods(testClass, SetUpServer.class, (setUpServer, method) -> {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " does not have exactly one parameter (Server.Builder)");
            }
            if (!parameterTypes[0].equals(WebServer.Builder.class)) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " does not have exactly one parameter (Server.Builder)");
            }
            try {
                method.setAccessible(true);
                method.invoke(null, builder);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Could not invoke method " + method, e);
            }
        });
    }

    private void addRouting(WebServer.Builder builder) {
        withStaticMethods(testClass, SetUpRoute.class, (setUpRoute, method) -> {
            // validate parameters
            String socketName = setUpRoute.value();
            boolean isDefaultSocket = socketName.equals(DEFAULT_SOCKET_NAME);

            SetUpRouteHandler methodConsumer = createRoutingMethodCall(method);

            extensions.forEach(it -> builder.socket(socketName,
                                                    (socket, route) -> it.updateListenerBuilder(socketName,
                                                                                                socket,
                                                                                                route)));

            if (isDefaultSocket) {
                builder.defaultSocket(socketBuilder -> {
                    methodConsumer.handle(socketName, builder, socketBuilder, builder);
                });
            } else {
                builder.socket(socketName, (socket, router) -> methodConsumer.handle(socketName, builder, socket, router));
            }
        });
    }

    private SetUpRouteHandler createRoutingMethodCall(Method method) {
        // @SetUpRoute may have parameters handled by different extensions
        List<ServerJunitExtension.ParamHandler> handlers = new ArrayList<>();

        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            Class<?> paramType = parameter.getType();

            // for each parameter, resolve a parameter handler
            boolean found = false;
            for (ServerJunitExtension extension : extensions) {
                Optional<? extends ServerJunitExtension.ParamHandler> paramHandler =
                        extension.setUpRouteParamHandler(paramType);
                if (paramHandler.isPresent()) {
                    // we care about the extension with the highest priority only
                    handlers.add(paramHandler.get());
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Method " + method + " has a parameter " + paramType + " that is "
                                                           + "not supported by any available testing extension");
            }
        }
        // now we have the same number of parameter handlers as we have parameters
        return (socketName, serverBuilder, listenerBuilder, routerBuilder) -> {
            Object[] values = new Object[handlers.size()];

            for (int i = 0; i < handlers.size(); i++) {
                ServerJunitExtension.ParamHandler<?> handler = handlers.get(i);
                values[i] = handler.get(socketName, serverBuilder, listenerBuilder, routerBuilder);
            }

            try {
                method.setAccessible(true);
                method.invoke(null, values);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Cannot invoke router/socket method", e);
            }

            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                ServerJunitExtension.ParamHandler handler = handlers.get(i);
                handler.handle(socketName, serverBuilder, listenerBuilder, routerBuilder, value);
            }
        };
    }

    private interface SetUpRouteHandler {
        void handle(String socketName,
                    WebServer.Builder serverBuilder,
                    ListenerConfiguration.Builder listenerBuilder,
                    Router.RouterBuilder<?> routerBuilder);
    }

}
