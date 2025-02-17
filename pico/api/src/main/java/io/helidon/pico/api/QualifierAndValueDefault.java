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

package io.helidon.pico.api;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.AnnotationAndValueDefault;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;

/**
 * Describes a {@link jakarta.inject.Qualifier} type annotation associated with a service being provided or dependant upon.
 * In Pico these are generally determined at compile time to avoid any use of reflection at runtime.
 */
public class QualifierAndValueDefault extends AnnotationAndValueDefault
        implements QualifierAndValue, Comparable<AnnotationAndValue> {

    /**
     * Constructor using the builder.
     *
     * @param b the builder
     * @see #builder()
     */
    protected QualifierAndValueDefault(Builder b) {
        super(b);
    }

    /**
     * Creates a {@link jakarta.inject.Named} qualifier.
     *
     * @param name the name
     * @return named qualifier
     */
    public static QualifierAndValueDefault createNamed(String name) {
        Objects.requireNonNull(name);
        return builder().typeName(CommonQualifiers.NAMED).value(name).build();
    }

    /**
     * Creates a qualifier from an annotation.
     *
     * @param qualifierType the qualifier type
     * @return qualifier
     */
    public static QualifierAndValueDefault create(Class<? extends Annotation> qualifierType) {
        Objects.requireNonNull(qualifierType);
        return builder().typeName(TypeNameDefault.create(qualifierType)).build();
    }

    /**
     * Creates a qualifier.
     *
     * @param qualifierType the qualifier type
     * @param val           the value
     * @return qualifier
     */
    public static QualifierAndValueDefault create(Class<? extends Annotation> qualifierType, String val) {
        Objects.requireNonNull(qualifierType);
        return builder().typeName(TypeNameDefault.create(qualifierType)).value(val).build();
    }

    /**
     * Creates a qualifier.
     *
     * @param qualifierTypeName the qualifier
     * @param val               the value
     * @return qualifier
     */
    public static QualifierAndValueDefault create(String qualifierTypeName, String val) {
        return builder()
                .typeName(TypeNameDefault.createFromTypeName(qualifierTypeName))
                .value(val)
                .build();
    }

    /**
     * Creates a qualifier.
     *
     * @param qualifierType the qualifier
     * @param val           the value
     * @return qualifier
     */
    public static QualifierAndValueDefault create(TypeName qualifierType, String val) {
        return builder()
                .typeName(qualifierType)
                .value(val)
                .build();
    }

    /**
     * Creates a qualifier.
     *
     * @param qualifierType the qualifier
     * @param vals           the values
     * @return qualifier
     */
    public static QualifierAndValueDefault create(TypeName qualifierType, Map<String, String> vals) {
        return builder()
                .typeName(qualifierType)
                .values(vals)
                .build();
    }

    /**
     * Converts from an {@link AnnotationAndValue} to a {@link QualifierAndValue}.
     *
     * @param annotationAndValue the annotation and value
     * @return the qualifier and value equivalent
     */
    public static QualifierAndValue convert(AnnotationAndValue annotationAndValue) {
        if (annotationAndValue instanceof QualifierAndValue) {
            return (QualifierAndValue) annotationAndValue;
        }

        return (QualifierAndValue) builder()
                .typeName(annotationAndValue.typeName())
                .values(annotationAndValue.values())
                .update(it -> annotationAndValue.value().ifPresent(it::value))
                .build();
    }

    @Override
    public int compareTo(AnnotationAndValue other) {
        return typeName().compareTo(other.typeName());
    }


    /**
     * Creates a builder for {@link QualifierAndValue}.
     *
     * @return a fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * The fluent builder.
     */
    public static class Builder extends AnnotationAndValueDefault.Builder {
        /**
         * Fluent builder constructor.
         */
        protected Builder() {
        }

        @Override
        public Builder typeName(TypeName val) {
            super.typeName(val);
            return this;
        }

        @Override
        public Builder value(String val) {
            super.value(val);
            return this;
        }

        @Override
        public Builder values(Map<String, String> val) {
            super.values(val);
            return this;
        }

        @Override
        public Builder type(Class<? extends Annotation> annoType) {
            super.type(annoType);
            return this;
        }

        /**
         * Build the instance.
         *
         * @return the built instance
         */
        @Override
        public QualifierAndValueDefault build() {
            return new QualifierAndValueDefault(this);
        }
    }

}
