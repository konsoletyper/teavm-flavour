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

import com.fasterxml.jackson.annotation.JsonFormat;
import org.teavm.flavour.mp.reflect.ReflectAnnotatedElement;

/**
 *
 * @author Alexey Andreev
 */
class DateFormatInformation {
    boolean asString;
    String pattern;
    String locale;

    private void read(ReflectAnnotatedElement annotations) {
        if (annotations == null) {
            return;
        }
        JsonFormat format = annotations.getAnnotation(JsonFormat.class);
        if (format == null) {
            return;
        }

        if (format.shape() == JsonFormat.Shape.STRING) {
            asString = true;
        } else {
            return;
        }

        pattern = format.pattern();
        if (pattern.isEmpty()) {
            pattern = null;
        }
        locale = format.locale();
    }

    public static DateFormatInformation get(ReflectAnnotatedElement annotations) {
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
