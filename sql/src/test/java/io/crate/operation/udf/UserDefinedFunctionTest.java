package io.crate.operation.udf;

import com.google.common.collect.ImmutableList;
import io.crate.analyze.symbol.Literal;
import io.crate.metadata.FunctionIdent;
import io.crate.types.DataTypes;
import org.junit.Test;

import java.util.HashMap;

public class UserDefinedFunctionTest {

    @Test
    public void testObject() {
        UserDefinedFunction udf =
            new UserDefinedFunction(
                new FunctionIdent("test", ImmutableList.of(DataTypes.OBJECT)),
                DataTypes.OBJECT,
                null,
                null,
                "function test(obj) {return obj.field}");

        Object result = udf.evaluate(new Literal<Obje>[]{Literal.of(new HashMap<String, Object>() {{
            put("obj", new HashMap<String, String>() {{
                put("foo", "bar");
            }});
        }})});
    }


}