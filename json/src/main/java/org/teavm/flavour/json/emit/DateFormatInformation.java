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

import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;

/**
 *
 * @author Alexey Andreev
 */
class DateFormatInformation {
    boolean asString;
    String pattern;
    String locale;

    private void read(AnnotationContainerReader annotations) {
        if (annotations == null) {
            return;
        }
        AnnotationReader formatAnnot = annotations.get("com.fasterxml.jackson.annotation.JsonFormat");
        if (formatAnnot == null) {
            return;
        }

        AnnotationValue shape = formatAnnot.getValue("shape");
        if (shape != null) {
            if (shape.getEnumValue().getFieldName().equals("STRING")) {
                asString = true;
            }
        }

        if (!asString) {
            return;
        }
        AnnotationValue patternAnnot = formatAnnot.getValue("pattern");
        if (patternAnnot != null) {
            pattern = patternAnnot.getString();
        }
        AnnotationValue localeAnnot = formatAnnot.getValue("locale");
        if (localeAnnot != null) {
            locale = localeAnnot.getString();
        }
    }

    public static DateFormatInformation get(AnnotationContainerReader annotations) {
        DateFormatInformation result = new DateFormatInformation();
        result.read(annotations);
        if (result.asString) {
            if (result.pattern == null) {
                result.pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";
            }
        }
        return result;
    }
}
