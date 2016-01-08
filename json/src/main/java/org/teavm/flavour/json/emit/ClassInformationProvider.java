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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
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
class ClassInformationProvider {
    private EmitterContext context;
    private Map<String, ClassInformation> cache = new HashMap<>();
    private EmitterDiagnostics diagnostics;
    private static Map<EmitterContext, ClassInformationProvider> instanceCache = new WeakHashMap<>();

    private ClassInformationProvider(EmitterContext context) {
        this.context = context;
        this.diagnostics = context.getDiagnostics();
    }

    public static ClassInformationProvider getInstance(EmitterContext context) {
        return instanceCache.computeIfAbsent(context, ClassInformationProvider::new);
    }

    public ClassInformation get(String className) {
        if (cache.containsKey(className)) {
            return cache.get(className);
        }
        ClassInformation info = createClassInformation(className);
        cache.put(className, info);
        if (info != null) {
            ReflectClass<?> cls = context.findClass(className);
            getSubTypes(info, cls);
        }
        return info;
    }

    private ClassInformation createClassInformation(String className) {
        ReflectClass<?> cls = context.findClass(className);
        if (cls == null) {
            return null;
        }

        ClassInformation information = new ClassInformation();
        information.className = className;
        if (className.equals("java.lang.Object")) {
            return information;
        }

        if (cls.getSuperclass() != null && !cls.getSuperclass().equals("java.lang.Object")) {
            ClassInformation parent = get(cls.getSuperclass().getName());
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
        scanCreators(information, cls);
        scanFields(information, cls);
        scanGetters(information, cls);
        scanSetters(information, cls);
        scanPropertyFields(information);

        return information;
    }

    private void getAutoDetectModes(ClassInformation information, ReflectClass<?> cls) {
        ClassInformation parent = information.parent;
        if (parent != null) {
            information.getterVisibility = parent.getterVisibility;
            information.isGetterVisibility = parent.isGetterVisibility;
            information.setterVisibility = parent.setterVisibility;
            information.fieldVisibility = parent.fieldVisibility;
            information.creatorVisibility = parent.creatorVisibility;
        }

        JsonAutoDetect annot = cls.getAnnotation(JsonAutoDetect.class);
        if (annot != null) {
            information.getterVisibility = getVisibility(annot.getterVisibility(), information.getterVisibility);
            information.isGetterVisibility = getVisibility(annot.isGetterVisibility(), information.isGetterVisibility);
            information.setterVisibility = getVisibility(annot.setterVisibility(), information.setterVisibility);
            information.fieldVisibility = getVisibility(annot.fieldVisibility(), information.fieldVisibility);
            information.creatorVisibility = getVisibility(annot.creatorVisibility(), information.creatorVisibility);
        }
    }

    private void getInheritance(ClassInformation information, ReflectClass<?> cls) {
        JsonTypeName typeName = cls.getAnnotation(JsonTypeName.class);
        information.typeName = "";
        if (typeName != null) {
            information.typeName = typeName.value();
        }
        if (information.typeName.isEmpty()) {
            information.typeName = getUnqualifiedName(cls.getName());
        }

        JsonTypeInfo typeInfo = cls.getAnnotation(JsonTypeInfo.class);
        if (typeInfo != null) {
            String defaultProperty = "";
            switch (typeInfo.use()) {
                case CLASS:
                    information.inheritance.value = InheritanceValue.CLASS;
                    defaultProperty = "@class";
                    break;
                case MINIMAL_CLASS:
                    information.inheritance.value = InheritanceValue.MINIMAL_CLASS;
                    defaultProperty = "@c";
                    break;
                case NAME:
                    information.inheritance.value = InheritanceValue.NAME;
                    defaultProperty = "@type";
                    break;
                case NONE:
                    information.inheritance.value = InheritanceValue.NONE;
                    break;
                default:
                    diagnostics.warning(null, "{{t0}}: unsupported value " + typeInfo.use() + " in {{t1}}",
                            cls, JsonTypeInfo.Id.class);
                    break;
            }

            if (information.inheritance.value != InheritanceValue.NONE) {
                switch (typeInfo.include()) {
                    case PROPERTY:
                        information.inheritance.key = InheritanceKey.PROPERTY;
                        break;
                    case WRAPPER_ARRAY:
                        information.inheritance.key = InheritanceKey.WRAPPER_ARRAY;
                        break;
                    case WRAPPER_OBJECT:
                        information.inheritance.key = InheritanceKey.WRAPPER_OBJECT;
                        break;
                    default:
                        diagnostics.warning(null, "{{t0}}: unsupported value " + typeInfo.include()
                                + " in {{t1}}", cls, JsonTypeInfo.As.class);
                        break;
                }
            }

            if (information.inheritance.key == InheritanceKey.PROPERTY) {
                String property = typeInfo.property();
                if (property.isEmpty()) {
                    property = defaultProperty;
                }
                information.inheritance.propertyName = property;
            }
        }
    }

    private void getIdentityInfo(ClassInformation information, ReflectClass<?> cls) {
        JsonIdentityInfo identity = cls.getAnnotation(JsonIdentityInfo.class);
        if (identity == null) {
            return;
        }

        Class<?> generator = identity.generator();
        if (generator.equals(ObjectIdGenerators.IntSequenceGenerator.class)) {
            information.idGenerator = IdGeneratorType.INTEGER;
        } else if (generator.equals(ObjectIdGenerators.PropertyGenerator.class)) {
            information.idGenerator = IdGeneratorType.PROPERTY;
        } else if (generator.equals(ObjectIdGenerators.None.class)) {
            information.idGenerator = IdGeneratorType.NONE;
        } else {
            information.idGenerator = IdGeneratorType.NONE;
            diagnostics.warning(null, "{{t0}}: unsupported identity generator {{t1}}", cls, generator);
        }

        if (information.idGenerator == IdGeneratorType.NONE) {
            information.idProperty = null;
        } else {
            information.idProperty = identity.property();
        }
    }

    static String getUnqualifiedName(String className) {
        return className.substring(Math.max(0, className.lastIndexOf('.')));
    }

    private void getIgnoredProperties(ClassInformation information, ReflectClass<?> cls) {
        JsonIgnoreProperties annot = cls.getAnnotation(JsonIgnoreProperties.class);
        if (annot == null) {
            return;
        }

        for (String name : annot.value()) {
            PropertyInformation property = information.properties.get(name);
            if (property == null) {
                property = new PropertyInformation();
                property.name = name;
                information.properties.put(name, property);
            }
            property.ignored = true;
        }
    }

    private Visibility getVisibility(JsonAutoDetect.Visibility visibility, Visibility defaultVisibility) {
        switch (visibility) {
            case DEFAULT:
                return defaultVisibility;
            case ANY:
                return Visibility.ANY;
            case NON_PRIVATE:
                return Visibility.NON_PRIVATE;
            case NONE:
                return Visibility.NONE;
            case PROTECTED_AND_PUBLIC:
                return Visibility.PROTECTED_AND_PUBLIC;
            case PUBLIC_ONLY:
                return Visibility.PUBLIC_ONLY;
        }
        throw new AssertionError("Unsupported visibility:" + visibility);
    }

    private void getSubTypes(ClassInformation information, ReflectClass<?> cls) {
        JsonSubTypes annot = cls.getAnnotation(JsonSubTypes.class);
        if (annot == null) {
            return;
        }

        for (JsonSubTypes.Type subtype : annot.value()) {
            Class<?> subclass = subtype.value();
            ClassInformation subtypeInformation = get(subclass.getName());
            if (subtypeInformation == null) {
                continue;
            }
            information.inheritance.subTypes.add(subtypeInformation);
            // TODO check whether name conflicts with one got from JsonTypeName
            if (!subtype.name().isEmpty()) {
                subtypeInformation.typeName = subtype.name();
            }
            if (subtypeInformation.typeName == null) {
                subtypeInformation.typeName = "";
            }
        }
    }

    private void scanGetters(ClassInformation information, ReflectClass<?> cls) {
        for (ReflectMethod method : cls.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (isGetterName(method.getName()) && method.getParameterCount() == 0
                    && method.getReturnType() != context.findClass(void.class)) {
                if (hasExplicitPropertyDeclaration(method)
                        || information.getterVisibility.match(method.getModifiers())) {
                    String propertyName = decapitalize(method.getName().substring(3));
                    addGetter(information, propertyName, method);
                }
            } else if (isBooleanName(method.getName()) && method.getParameterCount() == 0
                    && method.getReturnType() == context.findClass(boolean.class)) {
                if (hasExplicitPropertyDeclaration(method)
                        || information.isGetterVisibility.match(method.getModifiers())) {
                    String propertyName = decapitalize(method.getName().substring(2));
                    addGetter(information, propertyName, method);
                }
            }
        }
    }

    private void scanSetters(ClassInformation information, ReflectClass<?> cls) {
        for (ReflectMethod method : cls.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (isSetterName(method.getName()) && method.getParameterCount() == 1
                    && method.getReturnType() == context.findClass(void.class)) {
                if (hasExplicitPropertyDeclaration(method)
                        || information.setterVisibility.match(method.getModifiers())) {
                    String propertyName = decapitalize(method.getName().substring(3));
                    addSetter(information, propertyName, method);
                }
            }
        }
    }

    private void addGetter(ClassInformation information, String propertyName, ReflectMethod method) {
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

        if (property.ignored || isIgnored(method)) {
            property.ignored = true;
            return;
        }

        property.outputName = getPropertyName(method, property.outputName);
        PropertyInformation conflictingProperty = information.propertiesByOutputName.get(property.outputName);
        if (conflictingProperty != null) {
            SourceLocation location = new SourceLocation(method);
            diagnostics.error(location, "Duplicate property declaration " + propertyName + ". "
                    + "Already declared in {{c0}}", property.className);
            return;
        } else {
            information.propertiesByOutputName.put(property.outputName, property);
        }

        property.getter = method;
    }

    private void addSetter(ClassInformation information, String propertyName, ReflectMethod method) {
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

        if (property.ignored || isIgnored(method)) {
            property.ignored = true;
            return;
        }

        property.outputName = getPropertyName(method, property.outputName);
        PropertyInformation conflictingProperty = information.propertiesByOutputName.get(property.outputName);
        if (conflictingProperty != null) {
            SourceLocation location = new SourceLocation(method);
            diagnostics.error(location, "Duplicate property declaration " + propertyName + ". "
                    + "Already declared in {{c0}}", property.className);
            return;
        } else {
            information.propertiesByOutputName.put(property.outputName, property);
        }

        property.setter = method;
    }

    private void scanCreators(ClassInformation information, ReflectClass<?> cls) {
        ReflectMethod foundCreator = null;
        for (ReflectMethod method : cls.getDeclaredMethods()) {
            if (method.getAnnotation(JsonCreator.class) != null) {
                if (foundCreator != null) {
                    diagnostics.error(new SourceLocation(foundCreator), "Duplicate creators declared: "
                            + "{{m0}} and {{m1}}", foundCreator, method);
                    break;
                }
                foundCreator = method;
                if (!method.getName().equals("<init>") && Modifier.isStatic(method.getModifiers())) {
                    diagnostics.error(new SourceLocation(method), "Creator should be either constructor "
                            + " or static: {{m0}}", method);
                    continue;
                }
                information.constructor = method;
                for (int i = 0; i < method.getParameterCount(); ++i) {
                    PropertyInformation property = addParameter(information, method, i,
                            method.getParameterAnnotations(i), method.getParameterType(i));
                    information.constructorArgs.add(property);
                }
            }
        }
        if (information.constructor == null) {
            ReflectMethod defaultCtor = cls.getDeclaredMethod("<init>");
            if (defaultCtor != null) {
                information.constructor = defaultCtor;
            }
        }
    }

    private PropertyInformation addParameter(ClassInformation information, ReflectMethod creator, int index,
            ReflectAnnotatedElement annotations, ReflectClass<?> type) {
        PropertyInformation property = new PropertyInformation();
        property.className = information.className;
        property.outputName = getPropertyName(annotations, null);
        if (property.outputName == null) {
            diagnostics.error(new SourceLocation(creator), "Parameter #" + index + " name was not specified");
            return null;
        }
        property.name = property.outputName;

        PropertyInformation conflictingProperty = information.propertiesByOutputName.get(property.outputName);
        if (conflictingProperty != null) {
            diagnostics.error(null, "Duplicate property declaration " + property.outputName + ". "
                    + "Already declared in {{c0}}", property.className);
            return null;
        }

        information.properties.put(property.name, property);
        information.propertiesByOutputName.put(property.outputName, property);

        if (property.ignored || isIgnored(annotations)) {
            property.ignored = true;
            return property;
        }

        property.creatorParameterIndex = index;
        property.type = type;
        return property;
    }

    private void scanFields(ClassInformation information, ReflectClass<?> cls) {
        for (ReflectField field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (hasExplicitPropertyDeclaration(field) || information.getterVisibility.match(field.getModifiers())) {
                addField(information, field.getName(), field);
            }
        }
    }

    private void scanPropertyFields(ClassInformation information) {
        for (PropertyInformation property : information.properties.values()) {
            if (property.field != null) {
                continue;
            }
            ClassInformation ancestorInfo = information;
            while (ancestorInfo != null && ancestorInfo.properties.containsKey(property.name)) {
                ReflectClass<?> ancestor = context.findClass(ancestorInfo.className);
                ReflectField field = ancestor.getDeclaredField(property.name);
                if (field != null) {
                    addField(information, property.name, field);
                    break;
                }
                ancestorInfo = ancestorInfo.parent;
            }
        }
    }

    private void addField(ClassInformation information, String propertyName, ReflectField field) {
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

        if (property.ignored || isIgnored(field)) {
            property.ignored = true;
            return;
        }

        property.outputName = getPropertyName(field, property.outputName);
        PropertyInformation conflictingProperty = information.propertiesByOutputName.get(property.outputName);
        if (conflictingProperty != null) {
            diagnostics.error(null, "Duplicate property declaration " + propertyName + ". "
                    + "Already declared in {{c0}}", property.className);
            return;
        } else {
            information.propertiesByOutputName.put(property.outputName, property);
        }

        property.field = field;
        property.type = field.getType();
    }

    private boolean isIgnored(ReflectAnnotatedElement annotations) {
        return annotations.getAnnotation(JsonIgnore.class) != null;
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

    private String getPropertyName(ReflectAnnotatedElement annotations, String fallbackName) {
        JsonProperty annot = annotations.getAnnotation(JsonProperty.class);
        if (annot == null) {
            return fallbackName;
        }
        return !annot.value().isEmpty() ? annot.value() : fallbackName;
    }

    private boolean hasExplicitPropertyDeclaration(ReflectAnnotatedElement annotations) {
        return annotations.getAnnotation(JsonProperty.class) != null;
    }
}
