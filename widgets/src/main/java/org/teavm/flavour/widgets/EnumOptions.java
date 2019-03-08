/*
 *  Copyright 2019 ScraM-Team.
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
package org.teavm.flavour.widgets;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Slot;

@BindElement(name = "enum-options")
@BindTemplate("templates/flavour/widgets/enum-options.html")
public class EnumOptions extends AbstractWidget {
    private Supplier<Enum> enumToShow;

    public EnumOptions(Slot slot) {
        super(slot);
    }

    @BindAttribute(name = "enum")
    public void setEnumToShow(Supplier<Enum> enumToShow) {
        this.enumToShow = enumToShow;
    }

    public List<Object> getValues() {
        return Arrays.asList(enumToShow.get().getDeclaringClass().getEnumConstants());
    }

    public String displayName(Object enumValue) {
        return enumValue.toString();
    }
}
