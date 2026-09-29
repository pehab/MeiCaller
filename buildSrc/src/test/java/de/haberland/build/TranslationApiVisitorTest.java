package de.haberland.build;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

public class TranslationApiVisitorTest implements Opcodes {
    private byte[] fixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC, "TranslationLifecycle", null, "java/lang/Object", null);
        for (String name : new String[]{"onAttachedToWindow", "onDetachedFromWindow"}) {
            MethodVisitor m = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, name, "()V", null, null);
            m.visitCode();
            m.visitFieldInsn(GETSTATIC, TranslationApiVisitor.HELPER, "INSTANCE", "L" + TranslationApiVisitor.HELPER + ";");
            m.visitInsn(ACONST_NULL);
            m.visitMethodInsn(INVOKEVIRTUAL, TranslationApiVisitor.HELPER,
                    name.equals("onAttachedToWindow") ? "setViewTranslationCallback" : "clearViewTranslationCallback",
                    "(Landroid/view/View;)V", false);
            m.visitInsn(RETURN);
            m.visitMaxs(2, 0);
            m.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test public void patchedAttachAndDetachRunWithoutAndroidTranslationClasses() throws Exception {
        ClassWriter output = new ClassWriter(0);
        new ClassReader(fixture()).accept(new TranslationApiVisitor(output), 0);
        Class<?> patched = new Loader().define(output.toByteArray());
        patched.getMethod("onAttachedToWindow").invoke(null);
        patched.getMethod("onDetachedFromWindow").invoke(null);
    }

    @Test public void originalLifecycleFailsWhenApiHelperCannotLoad() throws Exception {
        Class<?> original = new Loader().define(fixture());
        try {
            original.getMethod("onAttachedToWindow").invoke(null);
            fail("Missing translation dependency should reproduce linkage failure");
        } catch (InvocationTargetException failure) {
            assertTrue(failure.getCause() instanceof NoClassDefFoundError);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void changedLifecycleFailsTheBuild() {
        ClassWriter output = new ClassWriter(0);
        TranslationApiVisitor visitor = new TranslationApiVisitor(output);
        visitor.visit(V1_8, ACC_PUBLIC, "ChangedView", null, "java/lang/Object", null);
        visitor.visitEnd();
    }

    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
}
