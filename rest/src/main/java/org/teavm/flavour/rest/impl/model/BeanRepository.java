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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodReader;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class BeanRepository {
    private static String[] meaningfulAnnotations = { JAXRSAnnotations.BEAN_PARAM,
            JAXRSAnnotations.PATH_PARAM, JAXRSAnnotations.HEADER_PARAM, JAXRSAnnotations.QUERY_PARAM };
    private ClassReaderSource classSource;
    private Diagnostics diagnostics;
    private Map<String, BeanModel> cache = new HashMap<>();

    public BeanRepository(ClassReaderSource classSource, Diagnostics diagnostics) {
        this.classSource = classSource;
        this.diagnostics = diagnostics;
    }

    public BeanModel getBean(String className) {
        return cache.computeIfAbsent(className, this::createBean);
    }

    private BeanModel createBean(String className) {
        ClassReader cls = classSource.get(className);
        if (cls == null) {
            return null;
        }

        BeanModel model = new BeanModel();
        model.className = className;
        if (cls.getParent() != null && !cls.getParent().equals("java.lang.Object")) {
            BeanModel parentModel = getBean(cls.getParent());
            if (parentModel != null) {
                model.properties.putAll(model.properties);
            }
        }

        detectProperties(model, cls);

        return model;
    }

    private void detectProperties(BeanModel model, ClassReader cls) {
        Set<String> inheritedProperties = new HashSet<>(model.properties.keySet());
        detectFields(cls, model, inheritedProperties);
        detectAccessors(cls, model, inheritedProperties);
        detectUsage(model);
    }

    private void detectFields(ClassReader cls, BeanModel model, Set<String> inheritedProperties) {
        for (FieldReader field : cls.getFields()) {
            if (field.hasModifier(ElementModifier.STATIC)) {
                continue;
            }
            if (field.getLevel() != AccessLevel.PUBLIC && hasMeaningfulAnnotation(field.getAnnotations())) {
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

    private void detectAccessors(ClassReader cls, BeanModel model, Set<String> inheritedProperties) {
        for (MethodReader method : cls.getMethods()) {
            if (method.hasModifier(ElementModifier.STATIC)) {
                continue;
            }
            if (method.getLevel() != AccessLevel.PUBLIC && hasMeaningfulAnnotation(method.getAnnotations())) {
                continue;
            }

            ValueType type;
            String propertyName = tryGetter(method);
            if (propertyName != null) {
                type = method.getResultType();
            } else {
                propertyName = trySetter(method);
                if (propertyName != null) {
                    type = method.parameterType(0);
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
                    if (hasMeaningfulAnnotation(method.getAnnotations())) {
                        diagnostics.error(new CallLocation(method.getReference()), "Inconsistent types for "
                                + "property " + propertyName + ": {{t0}} vs. {{t1}}", type, property.type);
                    }
                    continue;
                }
            }

            if (method.parameterCount() == 1) {
                property.setter = method;
            } else {
                property.getter = method;
            }
        }
    }

    private void detectUsage(BeanModel bean) {
        for (PropertyModel property : bean.properties.values()) {
            if (property.field != null) {
                detectUsage(bean, property, property.field.getAnnotations());
            }
            if (property.getter != null) {
                detectUsage(bean, property, property.getter.getAnnotations());
            }
            if (property.setter != null) {
                detectUsage(bean, property, property.setter.getAnnotations());
            }
        }
    }

    private void detectUsage(BeanModel bean, PropertyModel property, AnnotationContainerReader annotations) {
        if (!hasMeaningfulAnnotation(annotations)) {
            return;
        }
        int count = 0;
        for (String annot : meaningfulAnnotations) {
            if (annotations.get(annot) != null) {
                ++count;
                if (count > 1) {
                    diagnostics.error(null, "Property {{c0}}." + property.getName() + " should have only one "
                            + "of the following annotations: {{c0}}, {{c1}}, {{c2}}, {{c3}}",
                            bean.className, meaningfulAnnotations[0], meaningfulAnnotations[1],
                            meaningfulAnnotations[2], meaningfulAnnotations[3]);
                    return;
                }
            }
        }

        testScalarUsage(bean, property, annotations, JAXRSAnnotations.PATH_PARAM, Usage.PATH);
        testScalarUsage(bean, property, annotations, JAXRSAnnotations.QUERY_PARAM, Usage.QUERY);
        testScalarUsage(bean, property, annotations, JAXRSAnnotations.HEADER_PARAM, Usage.HEADER);
        testUsage(bean, property, annotations, JAXRSAnnotations.BEAN_PARAM, Usage.BEAN);
    }

    private void testScalarUsage(BeanModel bean, PropertyModel property, AnnotationContainerReader annotations,
            String annotationName, Usage desiredUsage) {
        testUsage(bean, property, annotations, annotationName, desiredUsage);
        AnnotationReader annot = annotations.get(annotationName);
        if (annot != null) {
            String newName = annot.getValue("value").getString();
            if (property.targetName != null && !newName.equals(property.targetName)) {
                diagnostics.error(null, "Property {{c0}}." + property.getName() + " has inconsistent JAX-RS "
                        + "annotations", bean.className);
            } else {
                property.targetName = newName;
            }
        }
    }

    private void testUsage(BeanModel bean, PropertyModel property, AnnotationContainerReader annotations,
            String annotationName, Usage desiredUsage) {
        AnnotationReader annot = annotations.get(annotationName);
        if (annot == null) {
            return;
        }
        if (property.usage != null) {
            if (property.usage != desiredUsage) {
                diagnostics.error(null, "Property {{c0}}." + property.getName() + " has inconsistent JAX-RS "
                        + "annotations", bean.className);
            }
        } else {
            property.usage = desiredUsage;
        }
    }

    private String tryGetter(MethodReader method) {
        if (method.getResultType() == ValueType.VOID || method.parameterCount() > 0) {
            return null;
        }
        return tryRemovePrefix("get", method.getName())
                .orElseGet(() -> method.getResultType() == ValueType.BOOLEAN
                        ? tryRemovePrefix("is", method.getName()).orElse(null)
                        : null);
    }

    private String trySetter(MethodReader method) {
        if (method.getResultType() != ValueType.VOID || method.parameterCount() != 1) {
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

    private boolean hasMeaningfulAnnotation(AnnotationContainerReader annotations) {
        for (String annot : meaningfulAnnotations) {
            if (annotations.get(annot) != null) {
                return true;
            }
        }
        return false;
    }
}
