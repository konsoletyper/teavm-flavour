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
package org.teavm.flavour.json.emit;

import java.util.HashMap;
import java.util.Map;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
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
class ClassInformationProvider {
    private ClassReaderSource classSource;
    private Map<String, ClassInformation> cache = new HashMap<>();
    private Diagnostics diagnostics;

    public ClassInformationProvider(ClassReaderSource classSource, Diagnostics diagnostics) {
        this.classSource = classSource;
        this.diagnostics = diagnostics;
    }

    public ClassInformation get(String className) {
        if (cache.containsKey(className)) {
            return cache.get(className);
        }
        ClassInformation info = createClassInformation(className);
        cache.put(className, info);
        return info;
    }

    private ClassInformation createClassInformation(String className) {
        ClassReader cls = classSource.get(className);
        if (cls == null) {
            return null;
        }

        ClassInformation information = new ClassInformation();
        information.className = className;

        if (cls.getParent() != null && !cls.getParent().equals("java.lang.Object")) {
            ClassInformation parent = get(cls.getParent());
            information.parent = parent;
            for (PropertyInformation property : parent.properties.values()) {
                property = property.clone();
                information.properties.put(property.name, property);
                information.propertiesByOutputName.put(property.outputName, property);
            }
            information.inheritance = information.parent.inheritance.clone();
            information.typeName = information.parent.typeName;
            information.idGenerator = information.parent.idGenerator;
            information.idProperty = information.parent.idProperty;
        }

        getAutoDetectModes(information, cls);
        getInheritance(information, cls);
        getIdentityInfo(information, cls);
        getIgnoredProperties(information, cls);
        scanFields(information, cls);
        scanGetters(information, cls);
        scanSetters(information, cls);

        return information;
    }

    private void getAutoDetectModes(ClassInformation information, ClassReader cls) {
        ClassInformation parent = information.parent;
        if (parent != null) {
            information.getterVisibility = parent.getterVisibility;
            information.isGetterVisibility = parent.isGetterVisibility;
            information.setterVisibility = parent.setterVisibility;
            information.fieldVisibility = parent.fieldVisibility;
            information.creatorVisibility = parent.creatorVisibility;
        }

        AnnotationReader annot = cls.getAnnotations().get("com.fasterxml.jackson.annotation.JsonAutoDetect");
        if (annot != null) {
            information.getterVisibility = getVisibility(annot, "getterVisibility", information.getterVisibility);
            information.isGetterVisibility = getVisibility(annot, "isGetterVisibility",
                    information.isGetterVisibility);
            information.setterVisibility = getVisibility(annot, "setterVisibility", information.setterVisibility);
            information.fieldVisibility = getVisibility(annot, "fieldVisibility", information.fieldVisibility);
            information.creatorVisibility = getVisibility(annot, "creatorVisibility", information.creatorVisibility);
        }
    }

    private void getInheritance(ClassInformation information, ClassReader cls) {
        AnnotationReader annot = cls.getAnnotations().get("com.fasterxml.jackson.annotation.JsonTypeName");
        if (annot != null) {
            AnnotationValue typeNameValue = annot.getValue("value");
            if (typeNameValue != null) {
                information.typeName = typeNameValue.getString();
            } else {
                information.typeName = getUnqualifiedName(cls.getName());
            }
        }

        annot = cls.getAnnotations().get("com.fasterxml.jackson.annotation.JsonTypeInfo");
        if (annot != null) {
            String defaultProperty = "";
            String use = annot.getValue("use").getEnumValue().getFieldName();
            switch (use) {
                case "CLASS":
                    information.inheritance.value = InheritanceValue.CLASS;
                    defaultProperty = "@class";
                    break;
                case "MINIMAL_CLASS":
                    information.inheritance.value = InheritanceValue.MINIMAL_CLASS;
                    defaultProperty = "@c";
                    break;
                case "NAME":
                    information.inheritance.value = InheritanceValue.NAME;
                    defaultProperty = "@type";
                    break;
                case "NONE":
                    information.inheritance.value = InheritanceValue.NONE;
                    break;
                default:
                    diagnostics.warning(null, "{{c0}}: unsupported value " + use + " in {{c1}}",
                            cls.getName(), "com.fasterxml.jackson.annotation.JsonTypeInfo$Id");
                    break;
            }

            if (information.inheritance.value != InheritanceValue.NONE) {
                AnnotationValue includeValue = annot.getValue("include");
                String include = includeValue != null ? includeValue.getEnumValue().getFieldName() : "PROPERTY";
                switch (include) {
                    case "PROPERTY":
                        information.inheritance.key = InheritanceKey.PROPERTY;
                        break;
                    case "WRAPPER_ARRAY":
                        information.inheritance.key = InheritanceKey.WRAPPER_ARRAY;
                        break;
                    case "WRAPPER_OBJECT":
                        information.inheritance.key = InheritanceKey.WRAPPER_OBJECT;
                        break;
                    default:
                        diagnostics.warning(null, "{{c0}}: unsupported value " + includeValue.getString() +
                                " in {{c1}}", cls.getName(), "com.fasterxml.jackson.annotation.JsonTypeInfo$As");
                        break;
                }
            }

            if (information.inheritance.key == InheritanceKey.PROPERTY) {
                AnnotationValue propertyValue = annot.getValue("property");
                String property = propertyValue != null ? propertyValue.getString() : "";
                if (property.isEmpty()) {
                    property = defaultProperty;
                }
                information.inheritance.propertyName = property;
            }
        }
    }

    private void getIdentityInfo(ClassInformation information, ClassReader cls) {
        AnnotationReader annot = cls.getAnnotations().get("com.fasterxml.jackson.annotation.JsonIdentityInfo");
        if (annot == null) {
            return;
        }

        ValueType generator = annot.getValue("generator").getJavaClass();
        if (generator.isObject("com.fasterxml.jackson.annotation.ObjectIdGenerators$IntSequenceGenerator")) {
            information.idGenerator = IdGeneratorType.INTEGER;
        } else if (generator.isObject("com.fasterxml.jackson.annotation.ObjectIdGenerators$PropertyGenerator")) {
            information.idGenerator = IdGeneratorType.PROPERTY;
        } else if (generator.isObject("com.fasterxml.jackson.annotation.ObjectIdGenerators$None")) {
            information.idGenerator = IdGeneratorType.NONE;
        } else {
            information.idGenerator = IdGeneratorType.NONE;
            diagnostics.warning(null, "{{c0}}: unsupported identity generator {{t1}}", cls.getName(), generator);
        }

        if (information.idGenerator == IdGeneratorType.NONE) {
            information.idProperty = null;
        } else {
            AnnotationValue propertyValue = annot.getValue("property");
            information.idProperty = propertyValue != null ? propertyValue.getString() : "@id";
        }
    }

    static String getUnqualifiedName(String className) {
        return className.substring(Math.max(0, className.lastIndexOf('.')));
    }

    private void getIgnoredProperties(ClassInformation information, ClassReader cls) {
        AnnotationReader annot = cls.getAnnotations().get("com.fasterxml.jackson.annotation.JsonIgnoreProperties");
        if (annot == null) {
            return;
        }

        AnnotationValue value = annot.getValue("value");
        if (value == null) {
            return;
        }

        for (AnnotationValue nameAnnot : value.getList()) {
            String name = nameAnnot.getString();
            PropertyInformation property = information.properties.get(name);
            if (property == null) {
                property = new PropertyInformation();
                property.name = name;
                information.properties.put(name, property);
            }
            property.ignored = true;
        }
    }

    private Visibility getVisibility(AnnotationReader annot, String fieldName, Visibility defaultVisibility) {
        AnnotationValue value = annot.getValue(fieldName);
        if (value == null) {
            return defaultVisibility;
        }
        Visibility visibility = Visibility.valueOf(value.getEnumValue().getFieldName());
        if (visibility == null || visibility == Visibility.DEFAULT) {
            return defaultVisibility;
        }
        return visibility;
    }

    private void scanGetters(ClassInformation information, ClassReader cls) {
        for (MethodReader method : cls.getMethods()) {
            if (method.hasModifier(ElementModifier.STATIC)) {
                continue;
            }
            if (isGetterName(method.getName()) && method.parameterCount() == 0 &&
                    method.getResultType() != ValueType.VOID) {
                if (hasExplicitPropertyDeclaration(method.getAnnotations()) ||
                        information.getterVisibility.match(method.getLevel())) {
                    String propertyName = decapitalize(method.getName().substring(3));
                    addGetter(information, propertyName, method);
                }
            } else if (isBooleanName(method.getName()) && method.parameterCount() == 0 &&
                    method.getResultType() == ValueType.BOOLEAN) {
                if (hasExplicitPropertyDeclaration(method.getAnnotations()) ||
                        information.isGetterVisibility.match(method.getLevel())) {
                    String propertyName = decapitalize(method.getName().substring(2));
                    addGetter(information, propertyName, method);
                }
            }
        }
    }

    private void scanSetters(ClassInformation information, ClassReader cls) {
        for (MethodReader method : cls.getMethods()) {
            if (method.hasModifier(ElementModifier.STATIC)) {
                continue;
            }
            if (isSetterName(method.getName()) && method.parameterCount() == 1 &&
                    method.getResultType() == ValueType.VOID) {
                if (hasExplicitPropertyDeclaration(method.getAnnotations()) ||
                        information.setterVisibility.match(method.getLevel())) {
                    String propertyName = decapitalize(method.getName().substring(3));
                    addSetter(information, propertyName, method);
                }
            }
        }
    }

    private void addGetter(ClassInformation information, String propertyName, MethodReader method) {
        PropertyInformation property = information.properties.get(propertyName);
        if (property != null) {
            information.propertiesByOutputName.remove(property.outputName);
        } else {
            property = new PropertyInformation();
            property.name = propertyName;
            property.outputName = propertyName;
            property.className = information.className;
            information.properties.put(propertyName, property);
        }

        if (property.ignored || isIgnored(method.getAnnotations())) {
            property.ignored = true;
            return;
        }

        property.outputName = getPropertyName(method.getAnnotations(), property.outputName);
        PropertyInformation conflictingProperty = information.propertiesByOutputName.get(property.outputName);
        if (conflictingProperty != null) {
            CallLocation location = new CallLocation(method.getReference());
            diagnostics.error(location, "Duplicate property declaration " + propertyName + ". " +
                    "Already declared in {{c0}}", property.className);
            return;
        } else {
            information.propertiesByOutputName.put(property.outputName, property);
        }

        property.getter = method.getDescriptor();
    }

    private void addSetter(ClassInformation information, String propertyName, MethodReader method) {
        PropertyInformation property = information.properties.get(propertyName);
        if (property != null) {
            information.propertiesByOutputName.remove(property.outputName);
        } else {
            property = new PropertyInformation();
            property.name = propertyName;
            property.outputName = propertyName;
            property.className = information.className;
            information.properties.put(propertyName, property);
        }

        if (property.ignored || isIgnored(method.getAnnotations())) {
            property.ignored = true;
            return;
        }

        property.outputName = getPropertyName(method.getAnnotations(), property.outputName);
        PropertyInformation conflictingProperty = information.propertiesByOutputName.get(property.outputName);
        if (conflictingProperty != null) {
            CallLocation location = new CallLocation(method.getReference());
            diagnostics.error(location, "Duplicate property declaration " + propertyName + ". " +
                    "Already declared in {{c0}}", property.className);
            return;
        } else {
            information.propertiesByOutputName.put(property.outputName, property);
        }

        property.setter = method.getDescriptor();
    }

    private void scanFields(ClassInformation information, ClassReader cls) {
        for (FieldReader field : cls.getFields()) {
            if (field.hasModifier(ElementModifier.STATIC)) {
                continue;
            }
            if (hasExplicitPropertyDeclaration(field.getAnnotations()) ||
                    information.getterVisibility.match(field.getLevel())) {
                addField(information, field.getName(), field);
            }
        }
    }

    private void addField(ClassInformation information, String propertyName, FieldReader field) {
        PropertyInformation property = information.properties.get(propertyName);
        if (property != null) {
            information.propertiesByOutputName.remove(property.outputName);
        } else {
            property = new PropertyInformation();
            property.name = propertyName;
            property.outputName = propertyName;
            property.className = information.className;
            information.properties.put(propertyName, property);
        }

        if (property.ignored || isIgnored(field.getAnnotations())) {
            property.ignored = true;
            return;
        }

        property.outputName = getPropertyName(field.getAnnotations(), property.outputName);
        PropertyInformation conflictingProperty = information.propertiesByOutputName.get(property.outputName);
        if (conflictingProperty != null) {
            diagnostics.error(null, "Duplicate property declaration " + propertyName + ". " +
                    "Already declared in {{c0}}", property.className);
            return;
        } else {
            information.propertiesByOutputName.put(property.outputName, property);
        }

        property.fieldName = field.getName();
        property.type = field.getType();
    }

    private boolean isIgnored(AnnotationContainerReader annotations) {
        return annotations.get("com.fasterxml.jackson.annotation.JsonIgnore") != null;
    }

    private boolean isGetterName(String name) {
        return name.startsWith("get") && name.length() > 3 && Character.toUpperCase(name.charAt(3)) == name.charAt(3);
    }

    private boolean isBooleanName(String name) {
        return name.startsWith("is") && name.length() > 2 && Character.toUpperCase(name.charAt(2)) == name.charAt(2);
    }

    private boolean isSetterName(String name) {
        return name.startsWith("set") && name.length() > 3 && Character.toUpperCase(name.charAt(3)) == name.charAt(3);
    }

    private String decapitalize(String name) {
        if (name.length() > 1 && name.charAt(1) == Character.toUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private String getPropertyName(AnnotationContainerReader annotations, String fallbackName) {
        AnnotationReader annot = annotations.get("com.fasterxml.jackson.annotation.JsonProperty");
        if (annot == null) {
            return fallbackName;
        }
        AnnotationValue name = annot.getValue("value");
        if (name == null) {
            return fallbackName;
        }
        return name.getString();
    }

    private boolean hasExplicitPropertyDeclaration(AnnotationContainerReader annotations) {
        return annotations.get("com.fasterxml.jackson.annotation.JsonProperty") != null;
    }
}
