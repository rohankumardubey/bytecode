/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.bytecode.expression;

import com.google.common.collect.ImmutableList;
import io.airlift.bytecode.BytecodeNode;
import io.airlift.bytecode.ClassDefinition;
import io.airlift.bytecode.ClassInfoLoader;
import io.airlift.bytecode.DynamicClassLoader;
import io.airlift.bytecode.MethodDefinition;
import io.airlift.bytecode.ParameterizedType;
import io.airlift.bytecode.Scope;
import io.airlift.bytecode.SmartClassWriter;
import org.objectweb.asm.ClassWriter;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.STATIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.BytecodeUtils.dumpBytecodeTree;
import static io.airlift.bytecode.BytecodeUtils.uniqueClassName;
import static io.airlift.bytecode.ParameterizedType.type;
import static org.testng.Assert.assertEquals;

public final class BytecodeExpressionAssertions
{
    public static final AtomicBoolean DUMP_BYTECODE_TREE = new AtomicBoolean();

    private BytecodeExpressionAssertions() {}

    static void assertBytecodeExpressionType(BytecodeExpression expression, ParameterizedType type)
    {
        assertEquals(expression.getType(), type);
    }

    public static void assertBytecodeExpression(BytecodeExpression expression, Object expected, String expectedRendering)
            throws Exception
    {
        assertBytecodeExpression(expression, expected, expectedRendering, Optional.empty());
    }

    public static void assertBytecodeExpression(BytecodeExpression expression, Object expected, String expectedRendering, Optional<ClassLoader> parentClassLoader)
            throws Exception
    {
        assertEquals(expression.toString(), expectedRendering);

        assertBytecodeNode(expression.ret(), expression.getType(), expected, parentClassLoader);
    }

    public static void assertBytecodeExpression(BytecodeExpression expression, Object expected, ClassLoader parentClassLoader)
            throws Exception
    {
        assertBytecodeExpression(expression, expected, Optional.of(parentClassLoader));
    }

    public static void assertBytecodeExpression(BytecodeExpression expression, Object expected, Optional<ClassLoader> parentClassLoader)
            throws Exception
    {
        assertBytecodeNode(expression.ret(), expression.getType(), expected, parentClassLoader);
    }

    public static void assertBytecodeNode(BytecodeNode node, ParameterizedType returnType, Object expected)
            throws Exception
    {
        assertBytecodeNode(node, returnType, expected, Optional.empty());
    }

    public static void assertBytecodeNode(BytecodeNode node, ParameterizedType returnType, Object expected, Optional<ClassLoader> parentClassLoader)
            throws Exception
    {
        assertEquals(execute(context -> node, returnType, parentClassLoader), expected);
    }

    public static void assertBytecodeNode(Function<Scope, BytecodeNode> nodeGenerator, ParameterizedType returnType, Object expected)
            throws Exception
    {
        assertBytecodeNode(nodeGenerator, returnType, expected, Optional.empty());
    }

    public static void assertBytecodeNode(Function<Scope, BytecodeNode> nodeGenerator, ParameterizedType returnType, Object expected, Optional<ClassLoader> parentClassLoader)
            throws Exception
    {
        assertEquals(execute(nodeGenerator, returnType, parentClassLoader), expected);
    }

    public static Object execute(Function<Scope, BytecodeNode> nodeGenerator, ParameterizedType returnType, Optional<ClassLoader> parentClassLoader)
            throws Exception
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                uniqueClassName("test", "Test"),
                type(Object.class));

        MethodDefinition method = classDefinition.declareMethod(a(PUBLIC, STATIC), "test", returnType);
        BytecodeNode node = nodeGenerator.apply(method.getScope());
        method.getBody().append(node);

        String tree = dumpBytecodeTree(classDefinition);
        if (DUMP_BYTECODE_TREE.get()) {
            System.out.println(tree);
        }

        DynamicClassLoader classLoader = new DynamicClassLoader(parentClassLoader.orElse(BytecodeExpressionAssertions.class.getClassLoader()));
        ClassWriter cw = new SmartClassWriter(ClassInfoLoader.createClassInfoLoader(ImmutableList.of(classDefinition), classLoader));
        classDefinition.visit(cw);

        Class<?> clazz = classLoader.defineClass(classDefinition.getType().getJavaClassName(), cw.toByteArray());
        return clazz.getMethod("test").invoke(null);
    }
}
