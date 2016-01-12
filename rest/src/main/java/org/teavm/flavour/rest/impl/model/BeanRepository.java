/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.flavour.rest.impl.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.ws.rs.BeanParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import org.teavm.flavour.mp.EmitterContext;
import org.teavm.flavour.mp.EmitterDiagnostics;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.SourceLocation;
import org.teavm.flavour.mp.reflect.ReflectAnnotatedElement;
import org.teavm.flavour.mp.reflect.ReflectField;
import org.teavm.flavour.mp.reflect.ReflectMethod;

/**
 *
 * @author Alexey Andreev
 */
public class BeanRepository {
    private static List<Class<? extends Annotation>> meaningfulAnnotations = Arrays.asList(BeanParam.class,
            PathParam.class, HeaderParam.class, QueryParam.class);
    private EmitterContext context;
    private EmitterDiagnostics diagnostics;
    private Map<ReflectClass<?>, BeanModel> cache = new HashMap<>();

    public BeanRepository(EmitterContext context) {
        this.context = context;
        this.diagnostics = context.getDiagnostics();
    }

    public BeanModel getBean(ReflectClass<?> cls) {
        return cache.computeIfAbsent(cls, this::createBean);
    }

    private BeanModel createBean(ReflectClass<?> cls) {
        BeanModel model = new BeanModel();
        model.type = cls;
        if (cls.getSuperclass() != null && !cls.getSuperclass().getName().equals("java.lang.Object")) {
            BeanModel parentModel = getBean(cls.getSuperclass());
            if (parentModel != null) {
                model.properties.putAll(model.properties);
            }
        }

        detectProperties(model, cls);

        return model;
    }

    private void detectProperties(BeanModel model, ReflectClass<?> cls) {
        Set<String> inheritedProperties = new HashSet<>(model.properties.keySet());
        detectFields(cls, model, inheritedProperties);
        detectAccessors(cls, model, inheritedProperties);
        detectUsage(model);
    }

    private void detectFields(ReflectClass<?> cls, BeanModel model, Set<String> inheritedProperties) {
        for (ReflectField field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!Modifier.isPublic(field.getModifiers()) && hasMeaningfulAnnotation(field)) {
                continue;
            }
            PropertyModel property = new PropertyModel();
            property.name = field.getName();
            property.field = field;
            property.type = field.getType();
            model.properties.put(field.getName(), property);
            inheritedProperties.remove(field.getName());
        }
    }

    private void detectAccessors(ReflectClass<?> cls, BeanModel model, Set<String> inheritedProperties) {
        for (ReflectMethod method : cls.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!Modifier.isPublic(method.getModifiers()) && hasMeaningfulAnnotation(method)) {
                continue;
            }

            ReflectClass<?> type;
            String propertyName = tryGetter(method);
            if (propertyName != null) {
                type = method.getReturnType();
            } else {
                propertyName = trySetter(method);
                if (propertyName != null) {
                    type = method.getParameterType(0);
                } else {
                    continue;
                }
            }

            if (inheritedProperties.remove(propertyName)) {
                model.properties.remove(propertyName);
            }
            PropertyModel property = model.properties.get(propertyName);
            if (property == null) {
                property = new PropertyModel();
                property.name = propertyName;
                property.type = type;
                model.properties.put(propertyName, property);
                inheritedProperties.remove(propertyName);
            } else {
                if (!type.equals(property.type)) {
                    if (hasMeaningfulAnnotation(method)) {
                        diagnostics.error(new SourceLocation(method), "Inconsistent types for "
                                + "property " + propertyName + ": {{t0}} vs. {{t1}}", type, property.type);
                    }
                    continue;
                }
            }

            if (method.getParameterCount() == 1) {
                property.setter = method;
            } else {
                property.getter = method;
            }
        }
    }

    private void detectUsage(BeanModel bean) {
        for (PropertyModel property : bean.properties.values()) {
            if (property.field != null) {
                detectUsage(bean, property, property.field);
            }
            if (property.getter != null) {
                detectUsage(bean, property, property.getter);
            }
            if (property.setter != null) {
                detectUsage(bean, property, property.setter);
            }
        }
    }

    private void detectUsage(BeanModel bean, PropertyModel property, ReflectAnnotatedElement annotations) {
        if (!hasMeaningfulAnnotation(annotations)) {
            return;
        }
        int count = 0;
        for (Class<? extends Annotation> annot : meaningfulAnnotations) {
            if (annotations.getAnnotation(annot) != null) {
                ++count;
                if (count > 1) {
                    diagnostics.error(null, "Property {{t0}}." + property.getName() + " should have only one "
                            + "of the following annotations: {{t1}}, {{t2}}, {{t3}}, {{t4}}",
                            bean.type, meaningfulAnnotations.get(0), meaningfulAnnotations.get(1),
                            meaningfulAnnotations.get(2), meaningfulAnnotations.get(3));
                    return;
                }
            }
        }

        testScalarUsage(bean, property, annotations, PathParam.class, Usage.PATH, PathParam::value);
        testScalarUsage(bean, property, annotations, QueryParam.class, Usage.QUERY, QueryParam::value);
        testScalarUsage(bean, property, annotations, HeaderParam.class, Usage.HEADER, HeaderParam::value);
        testUsage(bean, property, annotations, BeanParam.class, Usage.BEAN);
    }

    private <T extends Annotation> void testScalarUsage(BeanModel bean, PropertyModel property,
            ReflectAnnotatedElement annotations, Class<T> annotationType, Usage desiredUsage,
            Function<T, String> valueFunction) {
        testUsage(bean, property, annotations, annotationType, desiredUsage);
        T annot = annotations.getAnnotation(annotationType);
        if (annot != null) {
            String newName = valueFunction.apply(annot);
            if (property.targetName != null && !newName.equals(property.targetName)) {
                diagnostics.error(null, "Property {{t0}}." + property.getName() + " has inconsistent JAX-RS "
                        + "annotations", bean.type);
            } else {
                property.targetName = newName;
            }
        }
    }

    private void testUsage(BeanModel bean, PropertyModel property, ReflectAnnotatedElement annotations,
            Class<? extends Annotation> annotationType, Usage desiredUsage) {
        Annotation annot = annotations.getAnnotation(annotationType);
        if (annot == null) {
            return;
        }
        if (property.usage != null) {
            if (property.usage != desiredUsage) {
                diagnostics.error(null, "Property {{t0}}." + property.getName() + " has inconsistent JAX-RS "
                        + "annotations", bean.type);
            }
        } else {
            property.usage = desiredUsage;
        }
    }

    private String tryGetter(ReflectMethod method) {
        if (method.getReturnType() == context.findClass(void.class) || method.getParameterCount() > 0) {
            return null;
        }
        return tryRemovePrefix("get", method.getName())
                .orElseGet(() -> method.getReturnType() == context.findClass(boolean.class)
                        ? tryRemovePrefix("is", method.getName()).orElse(null)
                        : null);
    }

    private String trySetter(ReflectMethod method) {
        if (method.getReturnType() != context.findClass(void.class) || method.getParameterCount() != 1) {
            return null;
        }
        return tryRemovePrefix("set", method.getName()).orElse(null);
    }

    private Optional<String> tryRemovePrefix(String prefix, String name) {
        if (name.startsWith(prefix) && name.length() > prefix.length()) {
            if (isPropertyStart(name.charAt(prefix.length()))) {
                return Optional.of(propertyNameFromAccessor(name.substring(prefix.length())));
            }
        }
        return Optional.empty();
    }

    private boolean isPropertyStart(char c) {
        return Character.isAlphabetic(c) && Character.isUpperCase(c);
    }

    private String propertyNameFromAccessor(String name) {
        if (name.length() > 1) {
            return isPropertyStart(name.charAt(1)) ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
        } else {
            return name.toLowerCase();
        }
    }

    private boolean hasMeaningfulAnnotation(ReflectAnnotatedElement annotations) {
        for (Class<? extends Annotation> annot : meaningfulAnnotations) {
            if (annotations.getAnnotation(annot) != null) {
                return true;
            }
        }
        return false;
    }
}
