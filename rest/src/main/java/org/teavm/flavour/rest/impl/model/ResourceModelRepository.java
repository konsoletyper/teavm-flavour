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

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.teavm.flavour.rest.processor.HttpMethod;
import org.teavm.metaprogramming.Diagnostics;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.SourceLocation;
import org.teavm.metaprogramming.reflect.ReflectAnnotatedElement;
import org.teavm.metaprogramming.reflect.ReflectMethod;

public class ResourceModelRepository {
    private Diagnostics diagnostics;
    private BeanRepository beanRepository;
    private Map<ReflectClass<?>, ResourceModel> resources = new HashMap<>();

    public ResourceModelRepository(BeanRepository beanRepository) {
        this.diagnostics = Metaprogramming.getDiagnostics();
        this.beanRepository = beanRepository;
    }

    public ResourceModel getResource(ReflectClass<?> cls) {
        return resources.computeIfAbsent(cls, this::describeResource);
    }

    private ResourceModel describeResource(ReflectClass<?> cls) {
        if (!cls.isInterface() || !Modifier.isAbstract(cls.getModifiers())) {
            diagnostics.error(null, "Class {{c0}} is neither interface nor abstract class", cls);
        }

        ResourceModel resource = new ResourceModel(cls);
        applyInheritance(resource, cls);
        readPath(resource, cls);
        readMethods(resource, cls);

        return resource;
    }

    private void applyInheritance(ResourceModel resource, ReflectClass<?> cls) {
        for (ReflectClass<?> iface : cls.getInterfaces()) {
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

    private void readPath(ResourceModel resource, ReflectClass<?> cls) {
        Path pathAnnot = cls.getAnnotation(Path.class);
        if (pathAnnot != null) {
            resource.path = pathAnnot.value();
        } else if (resource.path == null) {
            diagnostics.error(null, "Class {{c0}} is not marked with {{c1}} annotation", cls.getName(),
                    JAXRSAnnotations.PATH);
            resource.path = "";
        }
    }

    private void readMethods(ResourceModel resource, ReflectClass<?> cls) {
        for (ReflectMethod method : cls.getDeclaredMethods()) {
            if (!Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            MethodModel methodModel = resource.methods.computeIfAbsent(new MethodKey(method),
                    k -> new MethodModel(method));
            readMethod(methodModel, method);
        }
    }

    private void readMethod(MethodModel model, ReflectMethod method) {
        readPath(model, method);
        readHttpMethod(model, method);
        readParameters(model, method);
        readMimeTypes(model, method);
        fillValues(model, method);
        validate(model, method);
    }

    private void readPath(MethodModel model, ReflectMethod method) {
        Path pathAnnot = method.getAnnotation(Path.class);
        if (pathAnnot != null) {
            dropInheritance(model);
            model.path = pathAnnot.value();
        }
    }

    private void readHttpMethod(MethodModel model, ReflectMethod method) {
        if (method.getAnnotation(GET.class) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.GET;
        } else if (method.getAnnotation(PUT.class) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.PUT;
        } else if (method.getAnnotation(POST.class) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.POST;
        } else if (method.getAnnotation(PATCH.class) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.PATCH;
        } else if (method.getAnnotation(DELETE.class) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.DELETE;
        } else if (method.getAnnotation(HEAD.class) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.HEAD;
        } else if (method.getAnnotation(OPTIONS.class) != null) {
            dropInheritance(model);
            model.httpMethod = HttpMethod.OPTIONS;
        }
    }

    private void readParameters(MethodModel model, ReflectMethod method) {
        for (int i = 0; i < method.getParameterCount(); ++i) {
            ReflectAnnotatedElement paramAnnotations = method.getParameterAnnotations(i);
            if (paramAnnotations.getAnnotation(PathParam.class) != null
                    || paramAnnotations.getAnnotation(QueryParam.class) != null
                    || paramAnnotations.getAnnotation(HeaderParam.class) != null
                    || paramAnnotations.getAnnotation(BeanParam.class) != null) {
                dropInheritance(model);
                break;
            }
        }
        if (model.inherited) {
            return;
        }

        for (int i = 0; i < method.getParameterCount(); ++i) {
            ParameterModel param = new ParameterModel();
            param.index = i;
            param.type = method.getParameterType(i);
            readParameter(param, method, method.getParameterAnnotations(i));
            model.parameters.add(param);
        }
    }

    private void readParameter(ParameterModel param, ReflectMethod method, ReflectAnnotatedElement annotations) {
        PathParam pathAnnot = annotations.getAnnotation(PathParam.class);
        QueryParam queryAnnot = annotations.getAnnotation(QueryParam.class);
        HeaderParam headerAnnot = annotations.getAnnotation(HeaderParam.class);
        BeanParam beanAnnot = annotations.getAnnotation(BeanParam.class);

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
            diagnostics.error(new SourceLocation(method), "Only one annotation of the following list "
                    + "enabled: {{t0}}, {{t1}}, {{t2}}, {{t3}}", BeanParam.class, QueryParam.class,
                    HeaderParam.class, PathParam.class);
        }

        if (pathAnnot != null) {
            param.usage = Usage.PATH;
            param.name = pathAnnot.value();
            validateScalarParameter(param, method);
        } else if (queryAnnot != null) {
            param.usage = Usage.QUERY;
            param.name = queryAnnot.value();
            validateScalarParameter(param, method);
        } else if (headerAnnot != null) {
            param.usage = Usage.HEADER;
            param.name = headerAnnot.value();
            validateScalarParameter(param, method);
        } else if (beanAnnot != null) {
            param.usage = Usage.BEAN;
            if (param.type.isArray() || param.type.isPrimitive()) {
                diagnostics.error(new SourceLocation(method), "Parameter #" + param.index
                        + " marked by {{t0}} must be of object type, actual type is {{t1}}",
                        BeanParam.class, param.type);
            }
        } else {
            param.usage = Usage.BODY;
        }
    }

    private void validateScalarParameter(ParameterModel param, ReflectMethod method) {
        if (!isSupportedParameterType(param.type)) {
            diagnostics.error(new SourceLocation(method), "Invalid parameter #" + param.getIndex()
                    + " type {{t0}}", param.getType());
        }
    }

    private boolean isSupportedParameterType(ReflectClass<?> type) {
        if (type.isPrimitive()) {
            return true;
        } else if (type.isArray()) {
            return false;
        } else {
            return Metaprogramming.findClass(String.class).isAssignableFrom(type)
                    || Metaprogramming.findClass(Number.class).isAssignableFrom(type)
                    || Metaprogramming.findClass(Enum.class).isAssignableFrom(type);
        }
    }

    private void readMimeTypes(MethodModel model, ReflectMethod method) {
        Produces produces = method.getAnnotation(Produces.class);
        if (produces != null) {
            model.produces.addAll(Arrays.asList(produces.value()));
        }
        Consumes consumes = method.getAnnotation(Consumes.class);
        if (consumes != null) {
            model.consumes.addAll(Arrays.asList(consumes.value()));
        }
    }

    private void fillValues(MethodModel model, ReflectMethod method) {
        for (ParameterModel param : model.parameters) {
            RootValuePath path = new RootValuePath(param);
            addValue(path, model, method);
        }
    }

    private void addValue(ValuePath path, MethodModel model, ReflectMethod method) {
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
                    diagnostics.error(new SourceLocation(method), "Multiple candidates for "
                            + "message body: " + model.body + " and " + path);
                } else {
                    model.body = path;
                }
                break;
        }
    }

    private void addScalar(ValuePath path, ReflectMethod method, Map<String, ValuePath> map) {
        if (map.containsKey(path.getName())) {
            diagnostics.error(new SourceLocation(method), "Multiple candidates for "
                    + path.getUsage().name().toLowerCase() + " parameter '" + path.getName() + "': "
                    + map.get(path.getName()) + " and " + path);
            return;
        }
        map.put(path.getName(), path);
    }

    private void fillBeanValues(ValuePath path, MethodModel model, ReflectMethod method) {
        if (path.getType().isArray() || path.getType().isPrimitive()) {
            return;
        }
        BeanModel bean = beanRepository.getBean(path.getType());
        if (bean == null) {
            return;
        }

        for (PropertyModel property : bean.properties.values()) {
            addValue(new PropertyValuePath(path, property), model, method);
        }
    }

    private void validate(MethodModel model, ReflectMethod method) {
        if (model.httpMethod == null) {
            model.httpMethod = HttpMethod.GET;
            diagnostics.error(new SourceLocation(method), "HTTP method not specified");
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
