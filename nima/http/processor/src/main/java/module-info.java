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

import io.helidon.nima.http.processor.HttpEndpointCreator;
import io.helidon.nima.http.processor.HttpMethodCreator;

/**
 * Annotation processor that generates HTTP Endpoints.
 */
module io.helidon.nima.http.processor {
    requires io.helidon.pico.api;
    requires io.helidon.pico.tools;
    requires io.helidon.pico.processor;
    requires java.compiler;
    requires io.helidon.builder.processor.spi;

    exports io.helidon.nima.http.processor;

    provides io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator
            with HttpEndpointCreator, HttpMethodCreator;
}
