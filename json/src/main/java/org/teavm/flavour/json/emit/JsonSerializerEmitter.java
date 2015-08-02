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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.serializer.BooleanSerializer;
import org.teavm.flavour.json.serializer.CharacterSerializer;
import org.teavm.flavour.json.serializer.DoubleSerializer;
import org.teavm.flavour.json.serializer.EnumSerializer;
import org.teavm.flavour.json.serializer.IntegerSerializer;
import org.teavm.flavour.json.serializer.JsonSerializer;
import org.teavm.flavour.json.serializer.JsonSerializerContext;
import org.teavm.flavour.json.serializer.ListSerializer;
import org.teavm.flavour.json.serializer.MapSerializer;
import org.teavm.flavour.json.serializer.StringSerializer;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.BooleanNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.PhiEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
class JsonSerializerEmitter {
    private DependencyAgent agent;
    private ClassReaderSource classSource;
    private ClassReader serializedClass;
    private ValueEmitter contextVar;
    private ValueEmitter valueVar;
    private ValueEmitter targetVar;
    private ProgramEmitter pe;
    private ClassInformationProvider informationProvider;
    private Set<String> serializableClasses = new HashSet<>();
    private static Map<String, String> predefinedSerializers = new HashMap<>();

    static {
        predefinedSerializers.put(Boolean.class.getName(), BooleanSerializer.class.getName());
        predefinedSerializers.put(Byte.class.getName(), IntegerSerializer.class.getName());
        predefinedSerializers.put(Short.class.getName(), IntegerSerializer.class.getName());
        predefinedSerializers.put(Character.class.getName(), CharacterSerializer.class.getName());
        predefinedSerializers.put(Integer.class.getName(), IntegerSerializer.class.getName());
        predefinedSerializers.put(Long.class.getName(), DoubleSerializer.class.getName());
        predefinedSerializers.put(Float.class.getName(), DoubleSerializer.class.getName());
        predefinedSerializers.put(Double.class.getName(), DoubleSerializer.class.getName());
        predefinedSerializers.put(BigInteger.class.getName(), DoubleSerializer.class.getName());
        predefinedSerializers.put(BigDecimal.class.getName(), DoubleSerializer.class.getName());
        predefinedSerializers.put(String.class.getName(), StringSerializer.class.getName());
    }

    public JsonSerializerEmitter(DependencyAgent agent) {
        this.agent = agent;
        this.classSource = agent.getClassSource();
        informationProvider = new ClassInformationProvider(classSource, agent.getDiagnostics());
    }

    public String addClassSerializer(String serializedClassName) {
        ClassReader cls = classSource.get(serializedClassName);
        if (cls == null) {
            return null;
        }
        if (serializableClasses.add(serializedClassName)) {
            if (tryGetPredefinedSerializer(serializedClassName) == null) {
                emitClassSerializer(serializedClassName);
            }
        }
        return getClassSerializer(serializedClassName);
    }

    public String getClassSerializer(String className) {
        String serializer = tryGetPredefinedSerializer(className);
        if (serializer == null) {
            serializer = serializableClasses.contains(className) ? className + "$$__serializer__$$" : null;
        }
        return serializer;
    }

    private String tryGetPredefinedSerializer(String className) {
        String serializer = predefinedSerializers.get(className);
        if (serializer == null) {
            if (classSource.isSuperType(Enum.class.getName(), className).orElse(false)) {
                serializer = EnumSerializer.class.getName();
            } else if (classSource.isSuperType(Map.class.getName(), className).orElse(false)) {
                serializer = MapSerializer.class.getName();
            } else if (classSource.isSuperType(Collection.class.getName(), className).orElse(false)) {
                serializer = ListSerializer.class.getName();
            }
        }
        return serializer;
    }

    private void emitClassSerializer(String serializedClassName) {
        ClassInformation information = informationProvider.get(serializedClassName);
        if (information == null) {
            return;
        }

        ClassHolder cls = new ClassHolder(serializedClassName + "$$__serializer__$$");
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(JsonSerializer.class.getName());

        emitSerializationMethod(information, cls);
        emitConstructor(cls);
        agent.submitClass(cls);
    }

    private void emitConstructor(ClassHolder cls) {
        MethodHolder ctor = new MethodHolder("<init>", ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);

        ProgramEmitter pe = ProgramEmitter.create(ctor, classSource);
        pe.var(0, cls)
                .invokeSpecial(JsonSerializer.class, "<init>")
                .exit();
        cls.addMethod(ctor);
    }

    private void emitSerializationMethod(ClassInformation information, ClassHolder cls) {
        serializedClass = classSource.get(information.className);
        if (serializedClass == null) {
            return;
        }

        ProgramEmitter oldPe = pe;
        ValueEmitter oldValueVar = valueVar;
        ValueEmitter oldTagetVar = targetVar;
        ValueEmitter oldContextVar = contextVar;
        ClassReader oldSerializedClass = serializedClass;
        try {
            MethodHolder method = new MethodHolder("serialize", ValueType.parse(JsonSerializerContext.class),
                    ValueType.parse(Object.class), ValueType.parse(Node.class));
            method.setLevel(AccessLevel.PUBLIC);

            pe = ProgramEmitter.create(method, classSource);
            contextVar = pe.var(1, JsonSerializerContext.class);
            valueVar = pe.var(2, Object.class).cast(ValueType.object(information.className));
            targetVar = pe.invoke(ObjectNode.class, "create", ObjectNode.class);

            emitIdentity(information);
            emitProperties(information);
            emitInheritance(information);

            targetVar.returnValue();
            cls.addMethod(method);
        } finally {
            pe = oldPe;
            valueVar = oldValueVar;
            targetVar = oldTagetVar;
            contextVar = oldContextVar;
            serializedClass = oldSerializedClass;
        }
    }

    private void emitIdentity(ClassInformation information) {
        switch (information.idGenerator) {
            case NONE:
                contextVar.invokeVirtual("touch", valueVar.cast(Object.class));
                break;
            case INTEGER: {
                emitIntegerIdentity(information);
                break;
            }
            case PROPERTY:
                break;
        }
    }

    private void emitIntegerIdentity(ClassInformation information) {
        ValueEmitter has = contextVar.invokeVirtual("hasId", boolean.class, valueVar.cast(Object.class));
        ValueEmitter id = pe.invoke(NumberNode.class, "create", NumberNode.class,
                contextVar.invokeVirtual("getId", int.class, valueVar.cast(Object.class)));

        pe.when(has.isTrue()).thenDo(() -> id.returnValue());
        targetVar.invokeVirtual("set", pe.constant(information.idProperty), id.cast(Node.class));
    }

    private void emitProperties(ClassInformation information) {
        for (PropertyInformation property : information.properties.values()) {
            if (property.ignored) {
                continue;
            }
            if (property.getter != null) {
                emitGetter(property);
            } else if (property.fieldName != null) {
                emitField(property);
            }
        }
    }

    private void emitInheritance(ClassInformation information) {
        if (information.inheritance.key == null) {
            return;
        }

        String typeName;
        switch (information.inheritance.value) {
            case CLASS:
                typeName = information.className;
                break;
            case MINIMAL_CLASS:
                typeName = ClassInformationProvider.getUnqualifiedName(information.className);
                break;
            case NAME:
                typeName = information.typeName != null ? information.typeName
                        : ClassInformationProvider.getUnqualifiedName(information.className);
                break;
            default:
                return;
        }

        switch (information.inheritance.key) {
            case PROPERTY: {
                ValueEmitter key = pe.constant(information.inheritance.propertyName);
                ValueEmitter value = pe.invoke(StringNode.class, "create", StringNode.class, pe.constant(typeName));
                targetVar.invokeVirtual("set", key, value.cast(Node.class));
                break;
            }
            case WRAPPER_OBJECT: {
                ValueEmitter wrapper = pe.invoke(ObjectNode.class, "create", ObjectNode.class);
                wrapper.invokeVirtual("set", pe.constant(typeName), targetVar.cast(Node.class));
                targetVar = wrapper.cast(Node.class);
                break;
            }
            case WRAPPER_ARRAY: {
                ValueEmitter wrapper = pe.invoke(ArrayNode.class, "create", ArrayNode.class);
                ValueEmitter key = pe.invoke(StringNode.class, "create", StringNode.class, pe.constant(typeName));
                wrapper.invokeVirtual("add", key.cast(Node.class));
                wrapper.invokeVirtual("add", targetVar.cast(Node.class));
                targetVar = wrapper.cast(Node.class);
                break;
            }
        }
    }

    private void emitGetter(PropertyInformation property) {
        MethodReference method = new MethodReference(property.className, property.getter);

        MethodDependency getterDep = agent.linkMethod(method, null);
        AnnotationContainerReader annotations = getterDep.getMethod() != null ? getterDep.getMethod().getAnnotations()
                : null;
        ValueEmitter propertyValue = convertValue(valueVar.invokeVirtual(method), method.getReturnType(),
                getterDep.getResult(), annotations);
        targetVar.invokeSpecial(ObjectNode.class, "set", pe.constant(property.outputName),
                propertyValue.cast(Node.class));
    }

    private void emitField(PropertyInformation property) {
        FieldReference field = new FieldReference(property.className, property.fieldName);

        FieldDependency dep = agent.linkField(field, null);
        AnnotationContainerReader annotations = dep.getField() != null ? dep.getField().getAnnotations() : null;
        ValueType type = dep.getField() != null ? dep.getField().getType() : ValueType.object("java.lang.Object");
        ValueEmitter propertyValue = convertValue(valueVar.getField(property.fieldName, type),
                type, dep.getValue(), annotations);
        targetVar.invokeVirtual("set", pe.constant(property.outputName), propertyValue.cast(Node.class));
    }

    private ValueEmitter convertValue(ValueEmitter value, final ValueType type, DependencyNode node,
            AnnotationContainerReader annotations) {
        if (type instanceof ValueType.Primitive) {
            return convertPrimitive(value, (ValueType.Primitive) type);
        } else {
            return convertNullable(value, type, node, annotations);
        }
    }

    private ValueEmitter convertNullable(ValueEmitter value, ValueType type, DependencyNode node,
            AnnotationContainerReader annotations) {
        BasicBlock exit = pe.prepareBlock();
        PhiEmitter result = pe.phi(Node.class, exit);

        pe.when(value.isNull())
                .thenDo(() -> value.propagateTo(result).jump(exit))
                .elseDo(() -> {
                    ValueEmitter notNullValue;
                    if (type instanceof ValueType.Array) {
                        notNullValue = convertArray(value, (ValueType.Array) type, node, annotations);
                    } else if (type instanceof ValueType.Object) {
                        notNullValue = convertObject(value, (ValueType.Object) type, node, annotations);
                    } else {
                        notNullValue = value;
                    }
                    notNullValue.propagateTo(result).jump(exit);
                });
        pe.enter(exit);
        return result.getValue();
    }

    private ValueEmitter convertPrimitive(ValueEmitter value, ValueType.Primitive type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return pe.invoke(BooleanNode.class, "get", BooleanNode.class, value);
            case BYTE:
            case SHORT:
            case CHARACTER:
            case INTEGER:
                return pe.invoke(NumberNode.class, "create", NumberNode.class, value.cast(int.class));
            case LONG:
            case FLOAT:
            case DOUBLE:
                return pe.invoke(NumberNode.class, "create", NumberNode.class, value.cast(double.class));
        }
        throw new AssertionError("Unknown primitive type: " + type);
    }

    private ValueEmitter convertArray(ValueEmitter value, ValueType.Array type, DependencyNode node,
            AnnotationContainerReader annotations) {
        ValueType itemType = type.getItemType();

        BasicBlock loopDecision = pe.prepareBlock();
        BasicBlock loopExit = pe.prepareBlock();

        PhiEmitter index = pe.phi(int.class, loopDecision);
        ValueEmitter json = pe.invoke(ArrayNode.class, "create", ArrayNode.class);
        ValueEmitter size = value.arrayLength();
        pe.constant(0).propagateTo(index);
        pe.jump(loopDecision);

        pe.enter(loopDecision);
        pe.when(index.getValue().isLessThan(size))
                .thenDo(() -> {
                    ValueEmitter item = value.getElement(index.getValue());
                    json.invokeVirtual("add", convertValue(item, itemType, node.getArrayItem(), annotations)
                            .cast(Node.class));
                    index.getValue().add(1).propagateTo(index);
                    pe.jump(loopDecision);
                })
                .elseDo(() -> pe.jump(loopExit));

        pe.enter(loopExit);
        return json;
    }

    private ValueEmitter convertObject(ValueEmitter value, ValueType.Object type, DependencyNode node,
            AnnotationContainerReader annotations) {
        if (type.getClassName().equals(String.class.getName())) {
            return pe.invoke(StringNode.class, "create", StringNode.class, value);
        } else if (classSource.isSuperType(Date.class.getName(), type.getClassName()).orElse(false)) {
            return convertDate(value, annotations);
        } else {
            final MethodReference serializeRef = new MethodReference(JSON.class, "serialize",
                    JsonSerializerContext.class, Object.class, Node.class);
            node.addConsumer(t -> agent.linkMethod(serializeRef, null).propagate(2, t));
            return pe.invoke(serializeRef, contextVar, value);
        }
    }

    private ValueEmitter convertDate(ValueEmitter value, AnnotationContainerReader annotations) {
        DateFormatInformation formatInfo = DateFormatInformation.get(annotations);
        if (formatInfo.asString) {
            ValueEmitter locale = formatInfo.locale != null
                    ? pe.construct(Locale.class, pe.constant(formatInfo.locale))
                    : pe.invoke(Locale.class, "getDefault", Locale.class);
            ValueEmitter format = pe.construct(SimpleDateFormat.class, pe.constant(formatInfo.pattern), locale);
            format.invokeVirtual("setTimeZone", pe.invoke(TimeZone.class, "getTimeZone", TimeZone.class,
                    pe.constant("GMT")));
            value = format.invokeVirtual("format", String.class, value.cast(Date.class));
            return pe.invoke(StringNode.class, "create", StringNode.class, value);
        } else {
            value = value.invokeVirtual("getTime", long.class).cast(double.class);
            return pe.invoke(NumberNode.class, "create", NumberNode.class, value);
        }
    }
}
