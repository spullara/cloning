package com.rits.cloning;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.objectweb.asm.commons.Method.getMethod;

/**
 * Compile a list of codes to execute down to a single method.
 */
public class CloneCompiler {
    private static AtomicInteger id = new AtomicInteger(0);
    private static final Method EXECUTE_METHOD = Method.getMethod("java.io.Writer execute(java.io.Writer, java.util.List)");

    public static IDeepCloner compile(Field[] fields) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        int classId = id.incrementAndGet();
        String className = "com.rits.cloning.cloners.Cloner" + classId;
        String internalClassName = className.replace(".", "/");
        cw.visit(Opcodes.V12, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, internalClassName, null, "java/lang/Object", new String[]{IDeepCloner.class.getName().replace(".", "/")});
        cw.visitSource("DeepClone", null);

        GeneratorAdapter cm = new GeneratorAdapter(Opcodes.ACC_PUBLIC, getMethod("void <init> ()"), null, null, cw);
        cm.loadThis();
        cm.invokeConstructor(Type.getType(Object.class), getMethod("void <init> ()"));
        {
            GeneratorAdapter gm = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, getMethod("java.lang.Object deepClone(java.lang.Object)"), null, null, cw);
            int objectToClone = gm.newLocal(Type.getType(Object.class));
            gm.loadArg(0);
            gm.storeLocal(objectToClone);

            for (Field field : fields) {
            }
            cm.returnValue();
            cm.endMethod();

            // Load writer and return it
            gm.loadLocal(objectToClone);
            gm.returnValue();
            gm.endMethod();
        }

        cw.visitEnd();
        return null;
    }
}
