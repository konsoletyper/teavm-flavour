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
package org.teavm.flavour.templates.emitting;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class OffsetToLineMapper {
    int[] lines;

    public void prepare(Reader reader) throws IOException {
        List<Integer> lineList = new ArrayList<>();
        boolean cr = false;
        int offset = 0;
        while (true) {
            int code = reader.read();
            if (code < 0) {
                if (cr) {
                    lineList.add(offset);
                }
                break;
            }
            ++offset;
            if (code == '\r') {
                cr = true;
            } else if (code == '\n') {
                cr = false;
                lineList.add(offset);
            } else if (cr) {
                cr = false;
                lineList.add(offset - 1);
            }
        }
        lines = new int[lineList.size()];
        for (int i = 0; i < lines.length; ++i) {
            lines[i] = lineList.get(i);
        }
    }

    public int getLine(int offset) {
        int line = Arrays.binarySearch(lines, offset);
        if (line < 0) {
            line = -line - 1;
        }
        return line;
    }
}
