/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.flavour.example.api;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import javax.ws.rs.QueryParam;
import org.teavm.flavour.json.JsonPersistable;

@JsonPersistable
public class OrderQueryDTO {
    @QueryParam("text")
    public String text;

    @QueryParam("item-id")
    public Integer itemId;

    @QueryParam("start-date")
    public String startDate;

    @QueryParam("end-date")
    public String endDate;

    public static DateFormat getDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    }
}
