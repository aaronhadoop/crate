/*
 * Licensed to Crate.io Inc. ("Crate.io") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate.io licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * To enable or use any of the enterprise features, Crate.io must have given
 * you permission to enable and use the Enterprise Edition of CrateDB and you
 * must have a valid Enterprise or Subscription Agreement with Crate.io.  If
 * you enable or use features that are part of the Enterprise Edition, you
 * represent and warrant that you have a valid Enterprise or Subscription
 * Agreement with Crate.io.  Your use of features of the Enterprise Edition
 * is governed by the terms and conditions of your Enterprise or Subscription
 * Agreement with Crate.io.
 */

package io.crate.operation.udf;

import io.crate.analyze.symbol.Function;
import io.crate.analyze.symbol.Literal;
import io.crate.analyze.symbol.Symbol;
import io.crate.data.Input;
import io.crate.metadata.FunctionIdent;
import io.crate.metadata.FunctionInfo;
import io.crate.metadata.Scalar;
import io.crate.metadata.TransactionContext;
import io.crate.types.ArrayType;
import io.crate.types.DataType;
import io.crate.types.GeoPointType;
import io.crate.types.ObjectType;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class UserDefinedFunction extends Scalar<Object, Object> {

//    private static final Map<String, String> ENGINES = new HashMap<String, String>(){{
//        put("javascript", "nashorn");
//    }};

    public static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("nashorn");

    private final DataType returnType;
    private final FunctionInfo info;
    private final Set<String> options;
    private final String language;
    private final CompiledScript compiled;

    public UserDefinedFunction(FunctionIdent ident, DataType returnType, Set<String> options, String language, String body) {
        this.language = language;
        this.info = new FunctionInfo(ident, returnType);
        this.returnType = info.returnType();
        this.options = options;
        compiled = compileFunction(body);
    }

    private CompiledScript compileFunction(String body) {
        try {
            return ((Compilable) ENGINE).compile(body);
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public FunctionInfo info() {
        return info;
    }

    @SafeVarargs
    @Override
    public final Object evaluate(Input<Object>... args) {
        Object[] values = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            values[i] = args[i].value();
        }
        Object result = null;
        try {
            Invocable invocable = (Invocable) compiled.getEngine();
            compiled.eval();
            result = invocable.invokeFunction(info.ident().name(), values);
        } catch (ScriptException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return result(result);
    }

    private Object result(Object result) {
        if (result instanceof ScriptObjectMirror) {
            ScriptObjectMirror scriptObject = (ScriptObjectMirror) result;

            if (returnType instanceof ArrayType && scriptObject.isArray()) {
                return scriptObject.values().stream()
                    .toArray(Object[]::new);
            } else if (returnType instanceof ObjectType) {
                Map<String, Object> object = new HashMap<>(scriptObject.size());
                for (Map.Entry<String, Object> a : scriptObject.entrySet()) {
                    object.put(a.getKey(), a.getValue());
                }
                return object;
            } else if (returnType instanceof GeoPointType) {
                return scriptObject.values().stream()
                    .toArray(Object[]::new);
            } else {
                return result;
            }
        }
        return result;
    }

    @Override
    public Symbol normalizeSymbol(Function symbol, TransactionContext transactionContext) {
        assert symbol.arguments().size() == 1 : "Number of arguments must be 1";
        Symbol argument = symbol.arguments().get(0);
        if (argument.symbolType().isValueSymbol()) {
            Object value = ((Input) argument).value();
            try {
                return Literal.of(returnType, returnType.value(value));
            } catch (ClassCastException | IllegalArgumentException e) {
            }
        }
        return symbol;
    }
}
