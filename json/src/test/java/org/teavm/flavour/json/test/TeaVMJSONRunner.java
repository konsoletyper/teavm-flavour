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
package org.teavm.flavour.json.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.tree.BooleanNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.metaprogramming.CompileTime;

@CompileTime
public final class TeaVMJSONRunner {
    private TeaVMJSONRunner() {
    }

    public static JsonNode serialize(Object value) {
        return convert(new JsonNodeFactory(false), JSON.serialize(value));
    }

    public static <T> T deserialize(String json, Class<T> type) {
        return JSON.deserialize(Node.parse(json), type);
    }

    public static JsonNode convert(JsonNodeFactory nf, Node node) {
        if (node.isMissing() || node.isNull()) {
            return nf.nullNode();
        } else if (node.isBoolean()) {
            BooleanNode booleanNode = (BooleanNode)node;
            return nf.booleanNode(booleanNode.getValue());
        } else if (node.isNumber()) {
            NumberNode numberNode = (NumberNode)node;
            if (numberNode.isInt()) {
                return nf.numberNode(numberNode.getIntValue());
            } else {
                return nf.numberNode(numberNode.getValue());
            }
        } else if (node.isString()) {
            StringNode stringNode = (StringNode)node;
            return nf.textNode(stringNode.getValue());
        } else if (node.isArray()) {
            ArrayNode result = nf.arrayNode();
            org.teavm.flavour.json.tree.ArrayNode source = (org.teavm.flavour.json.tree.ArrayNode)node;
            for (int i = 0; i < source.size(); ++i) {
                result.add(convert(nf, source.get(i)));
            }
            return result;
        } else if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode result = nf.objectNode();
            ObjectNode objectNode = (ObjectNode)node;
            for (String key : objectNode.allKeys()) {
                result.replace(key, convert(nf, objectNode.get(key)));
            }
            return result;
        } else {
            throw new IllegalArgumentException("Can't convert this JSON node");
        }
    }
}
