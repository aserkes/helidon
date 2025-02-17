/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;
import io.helidon.microprofile.scheduling.SchedulingCdiExtension;

/**
 * CDI Scheduling implementation.
 */
@Preview
@Feature(value = "Scheduling",
        description = "Task scheduling",
        in = HelidonFlavor.MP,
        path = "Scheduling"
)
module io.helidon.microprofile.scheduling {
    requires static io.helidon.common.features.api;

    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires io.helidon.common.configurable;
    requires io.helidon.config;
    requires io.helidon.config.mp;
    requires io.helidon.microprofile.cdi;
    requires io.helidon.microprofile.config;
    requires io.helidon.scheduling;

    exports io.helidon.microprofile.scheduling;
    opens io.helidon.microprofile.scheduling;

    provides jakarta.enterprise.inject.spi.Extension with SchedulingCdiExtension;
}
