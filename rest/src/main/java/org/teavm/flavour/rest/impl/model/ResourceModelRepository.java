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
import java.util.Map;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.rest.processor.HttpMethod;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class ResourceModelRepository {
    private Diagnostics diagnostics;
    private ClassReaderSource classSource;
    private BeanRepository beanRepository;
    private Map<String, ResourceModel> resources = new HashMap<>();

    public ResourceModelRepository(Diagnostics diagnostics, ClassReaderSource classSource,
            BeanRepository beanRepository) {
        this.diagnostics = diagnostics;
        this.classSource = classSource;
        this.beanRepository = beanRepository;
    }

    public ResourceModel getResource(String className) {
        return resources.computeIfAbsent(className, this::describeResource);
    }

    private ResourceModel describeResource(String className) {
        ClassReader cls = classSource.get(className);
        if (cls == null) {
            return null;
        }
        if (!cls.hasModifier(ElementModifier.INTERFACE) || !cls.hasModifier(ElementModifier.ABSTRACT)) {
            diagnostics.error(null, "Class {{c0}} is neither interface nor abstract class", className);
        }

        ResourceModel resource = new ResourceModel(className);
        applyInheritance(resource, cls);
        readPath(resource, cls);
        readMethods(resource, cls);

        return resource;
    }

    private void applyInheritance(ResourceModel resource, ClassReader cls) {
        for (String iface : cls.getInterfaces()) {
            ResourceModel superResource = getResource(iface);
            if (superResource != null) {
                resource.methods.putAll(superResource.methods);
                for (MethodModel method : resource.methods.values()) {
                    method.inherited = true;
                }
                resource.path = superResource.path;
            }
        }
    }

    private void readPath(ResourceModel resource, ClassReader cls) {
        AnnotationReader pathAnnot = cls.getAnnotations().get(JAXRSAnnotations.PATH);
        if (pathAnnot != null) {
            resource.path = pathAnnot.getValue("value").getString();
        } else if (resource.path == null) {
            diagnostics.error(null, "Class {{c0}} is not marked with {{c1}} annotation", cls.getName(),
                    JAXRSAnnotations.PATH);
            resource.path = "";
        }
    }

    private void readMethods(ResourceModel resource, ClassReader cls) {
        for (MethodReader method : cls.getMethods()) {
            if (!method.hasModifier(ElementModifier.ABSTRACT)) {
                continue;
            }
            MethodModel methodModel = resource.methods.computeIfAbsent(method.getDescriptor(), MethodModel::new);
            readMethod(methodModel, method);
        }
    }

    private void readMethod(MethodModel model, MethodReader method) {
        readPath(model, method);
        readHttpMethod(model, method);
        readParameters(model, method);
        fillValues(model, method);
        validate(model, method);
    }

    private void readPath(MethodModel model, MethodReader method) {
        AnnotationReader pathAnnot = method.getAnnotations().get(JAXRSAnnotations.PATH);
        if (pathAnnot != null) {
            dropInheritance(model);
            model.path = pathAnnot.getValue("value").getString();
        }
    }

    private void readHttpMethod(MethodModel model, MethodReader method) {
        if (method.getAnnotations().get(JAXRSAnnotations.GET) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.GET;
        } else if (method.getAnnotations().get(JAXRSAnnotations.PUT) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.PUT;
        } else if (method.getAnnotations().get(JAXRSAnnotations.POST) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.POST;
        } else if (method.getAnnotations().get(JAXRSAnnotations.DELETE) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.DELETE;
        } else if (method.getAnnotations().get(JAXRSAnnotations.HEAD) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.HEAD;
        } else if (method.getAnnotations().get(JAXRSAnnotations.OPTIONS) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.OPTIONS;
        }
    }

    private void readParameters(MethodModel model, MethodReader method) {
        AnnotationContainerReader[] annotations = method.getParameterAnnotations();
        for (AnnotationContainerReader paramAnnotations : annotations) {
            if (paramAnnotations.get(JAXRSAnnotations.PATH_PARAM) != null
                    || paramAnnotations.get(JAXRSAnnotations.QUERY_PARAM) != null
                    || paramAnnotations.get(JAXRSAnnotations.HEADER_PARAM) != null
                    || paramAnnotations.get(JAXRSAnnotations.BEAN_PARAM) != null) {
                dropInheritance(model);
                break;
            }
        }
        if (model.inherited) {
            return;
        }

        for (int i = 0; i < method.parameterCount(); ++i) {
            ParameterModel param = new ParameterModel();
            param.index = i;
            param.type = method.parameterType(i);
            readParameter(param, method, annotations[i]);
        }
    }

    private void readParameter(ParameterModel param, MethodReader method,
            AnnotationContainerReader annotations) {
        AnnotationReader pathAnnot = annotations.get(JAXRSAnnotations.PATH_PARAM);
        AnnotationReader queryAnnot = annotations.get(JAXRSAnnotations.QUERY_PARAM);
        AnnotationReader headerAnnot = annotations.get(JAXRSAnnotations.HEADER_PARAM);
        AnnotationReader beanAnnot = annotations.get(JAXRSAnnotations.BEAN_PARAM);

        int annotCount = 0;
        if (pathAnnot != null) {
            ++annotCount;
        }
        if (queryAnnot != null) {
            ++annotCount;
        }
        if (headerAnnot != null) {
            ++annotCount;
        }
        if (beanAnnot != null) {
            ++annotCount;
        }

        if (annotCount > 1) {
            diagnostics.error(new CallLocation(method.getReference()), "Only one annotation of the following list "
                    + "enabled: {{c0}}, {{c1}}, {{c2}}, {{c3}}", JAXRSAnnotations.BEAN_PARAM,
                    JAXRSAnnotations.QUERY_PARAM, JAXRSAnnotations.HEADER_PARAM, JAXRSAnnotations.QUERY_PARAM);
        }

        if (pathAnnot != null) {
            param.usage = Usage.PATH;
            param.name = pathAnnot.getValue("value").getString();
            validateScalarParameter(param, method);
        } else if (queryAnnot != null) {
            param.usage = Usage.QUERY;
            param.name = queryAnnot.getValue("value").getString();
            validateScalarParameter(param, method);
        } else if (headerAnnot != null) {
            param.usage = Usage.HEADER;
            param.name = headerAnnot.getValue("value").getString();
            validateScalarParameter(param, method);
        } else if (beanAnnot != null) {
            param.usage = Usage.BEAN;
            if (!(param.type instanceof ValueType.Object)) {
                diagnostics.error(new CallLocation(method.getReference()), "Parameter #" + param.index
                        + " marked by {{c0}} must be of object type, actual type is {{t1}}",
                        JAXRSAnnotations.BEAN_PARAM, param.type);
            }
        } else {
            param.usage = Usage.BODY;
        }
    }

    private void validateScalarParameter(ParameterModel param, MethodReader method) {
        if (!isSupportedParameterType(param.type)) {
            diagnostics.error(new CallLocation(method.getReference()), "Invalid parameter #" + param.getIndex()
                    + " type {{t0}}", param.getType());
        }
    }

    private boolean isSupportedParameterType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            return true;
        } else if (type instanceof ValueType.Object) {
            ValueType.Object obj = (ValueType.Object) type;
            return classSource.isSuperType("java.lang.String", obj.getClassName()).orElse(false)
                    || classSource.isSuperType("java.lang.Number", obj.getClassName()).orElse(false);
        } else {
            return false;
        }
    }

    private void fillValues(MethodModel model, MethodReader method) {
        for (ParameterModel param : model.parameters) {
            RootValuePath path = new RootValuePath(param);
            addValue(path, model, method);
        }
    }

    private void addValue(ValuePath path, MethodModel model, MethodReader method) {
        switch (path.getUsage()) {
            case PATH:
                addScalar(path, method, model.pathParameters);
                break;
            case QUERY:
                addScalar(path, method, model.queryParameters);
                break;
            case HEADER:
                addScalar(path, method, model.headerParameters);
                break;
            case BEAN:
                fillBeanValues(path, model, method);
                break;
            case BODY:
                if (model.body != null) {
                    diagnostics.error(new CallLocation(method.getReference()), "Multiple candidates for "
                            + "message body: " + model.body + " and " + path);
                } else {
                    model.body = path;
                }
        }
    }

    private void addScalar(ValuePath path, MethodReader method, Map<String, ValuePath> map) {
        if (map.containsKey(path.getName())) {
            diagnostics.error(new CallLocation(method.getReference()), "Multiple candidates for "
                    + path.getUsage().name().toLowerCase() + " parameter '" + path.getName() + "': "
                    + map.get(path.getName()) + " and " + path);
            return;
        }
        map.put(path.getName(), path);
    }

    private void fillBeanValues(ValuePath path, MethodModel model, MethodReader method) {
        if (!(path.getType() instanceof ValueType.Object)) {
            return;
        }
        String className = ((ValueType.Object) path.getType()).getClassName();
        BeanModel bean = beanRepository.getBean(className);
        if (bean == null) {
            return;
        }

        for (PropertyModel property : bean.properties.values()) {
            addValue(new PropertyValuePath(path, property), model, method);
        }
    }

    private void validate(MethodModel model, MethodReader method) {
        if (model.httpMethod == null) {
            model.httpMethod = HttpMethod.GET;
            diagnostics.error(new CallLocation(method.getReference()), "HTTP method not specified");
        }
    }

    private void dropInheritance(MethodModel model) {
        if (!model.inherited) {
            return;
        }
        model.inherited = true;
        model.httpMethod = null;
        model.pathParameters.clear();
        model.queryParameters.clear();
        model.headerParameters.clear();
        model.path = "";
        model.pathParameters.clear();
        model.queryParameters.clear();
        model.headerParameters.clear();
    }
}
