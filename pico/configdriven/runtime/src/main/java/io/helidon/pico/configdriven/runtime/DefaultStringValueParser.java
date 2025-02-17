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

package io.helidon.pico.configdriven.runtime;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.config.spi.StringValueParser;
import io.helidon.pico.api.PicoException;

/**
 * Default implementation of {@link StringValueParser}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class DefaultStringValueParser implements StringValueParser {

    private static final Map<Class, StringValueParser> MAP = new HashMap<>();
    static {
        MAP.put(int.class, IntegerParser::parse);
        MAP.put(Integer.class, IntegerParser::parse);
        MAP.put(long.class, LongParser::parse);
        MAP.put(Long.class, LongParser::parse);
        MAP.put(float.class, FloatParser::parse);
        MAP.put(Float.class, FloatParser::parse);
        MAP.put(double.class, DoubleParser::parse);
        MAP.put(Double.class, DoubleParser::parse);
        MAP.put(boolean.class, BooleanParser::parse);
        MAP.put(Boolean.class, BooleanParser::parse);
        MAP.put(char[].class, CharArrayParser::parse);
        MAP.put(Duration.class, DurationParser::parse);
    }

    DefaultStringValueParser() {
    }

    @Override
    public <R> Optional<R> parse(String val,
                                 Class<R> type) {
        if (String.class == type) {
            return (Optional<R>) Optional.ofNullable(val);
        }

        StringValueParser parser = MAP.get(type);
        if (parser == null) {
            throw new IllegalStateException("Don't know how to parse String -> " + type);
        }

        return parser.parse(val, type);
    }


    static class IntegerParser {
        public static Optional parse(String val,
                                     Class ignoredType) {
            return Optional.ofNullable(null == val ? null : Integer.valueOf(val));
        }
    }

    static class LongParser {
        public static Optional parse(String val,
                                     Class ignoredType) {
            return Optional.ofNullable(null == val ? null : Long.valueOf(val));
        }
    }

    static class FloatParser {
        public static Optional parse(String val,
                                     Class ignoredType) {
            return Optional.ofNullable(null == val ? null : Float.valueOf(val));
        }
    }

    static class DoubleParser {
        public static Optional parse(String val,
                                     Class ignoredType) {
            return Optional.ofNullable(null == val ? null : Double.valueOf(val));
        }
    }

    static class BooleanParser {
        public static Optional parse(String val,
                                     Class ignoredType) {
            return Optional.ofNullable(null == val ? null : Boolean.valueOf(val));
        }
    }

    static class CharArrayParser {
        public static Optional parse(String val,
                                     Class ignoredType) {
            return Optional.ofNullable(null == val ? null : val.toCharArray());
        }
    }

    static class DurationParser {
        public static Optional parse(String val,
                                     Class ignoredType) {
            try {
                return Optional.ofNullable(null == val ? null : Duration.parse(val));
            } catch (Exception e) {
                throw new PicoException("Failed to parse duration: \"" + val + "\"", e);
            }
        }
    }

}
