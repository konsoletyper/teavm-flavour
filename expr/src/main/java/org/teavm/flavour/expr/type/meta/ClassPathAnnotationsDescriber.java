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
package org.teavm.flavour.expr.type.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
abstract class ClassPathAnnotationsDescriber implements AnnotationsDescriber {
    private Map<Annotation, ClassPathAnnotationDescriber> annotationCache = new HashMap<>();
    private AnnotationDescriber[] annotations;

    abstract AnnotatedElement getAnnotatedElement();

    abstract ClassPathClassDescriberRepository getRepository();

    @Override
    public AnnotationDescriber[] getAnnotations() {
        if (annotations == null) {
            Annotation[] javaAnnotations = getAnnotatedElement().getDeclaredAnnotations();
            annotations = new AnnotationDescriber[javaAnnotations.length];
            for (int i = 0; i < javaAnnotations.length; ++i) {
                annotations[i] = getAnnotation(javaAnnotations[i]);
            }
        }
        return annotations.clone();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AnnotationDescriber getAnnotation(String className) {
        Class<Annotation> cls;
        try {
            cls = (Class<Annotation>)Class.forName(className, false, getRepository().classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
        Annotation javaAnnotation = getAnnotatedElement().getAnnotation(cls);
        return javaAnnotation != null ? getAnnotation(javaAnnotation) : null;
    }

    private AnnotationDescriber getAnnotation(Annotation javaAnnotation) {
        ClassPathAnnotationDescriber annotation = annotationCache.get(javaAnnotation);
        if (annotation == null) {
            annotation = new ClassPathAnnotationDescriber(javaAnnotation);
            annotationCache.put(javaAnnotation, annotation);
        }
        return annotation;
    }
}
