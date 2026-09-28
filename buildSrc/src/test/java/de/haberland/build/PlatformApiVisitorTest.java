package de.haberland.build;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.Assert.*;

public class PlatformApiVisitorTest implements Opcodes {
    private byte[] caller(boolean font) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC, "Fixture", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "read",
                font ? "(Landroid/content/res/Configuration;)I" : "()I", null, null);
        method.visitCode();
        if (font) {
            method.visitVarInsn(ALOAD, 0);
            method.visitFieldInsn(GETFIELD, "android/content/res/Configuration", "fontWeightAdjustment", "I");
        } else {
            method.visitInsn(ICONST_3); // status + navigation bar bits must survive the fallback
            method.visitMethodInsn(INVOKESTATIC, "android/view/WindowInsets$Type", "systemOverlays", "()I", false);
            method.visitInsn(IOR);
        }
        method.visitInsn(IRETURN);
        method.visitMaxs(2, font ? 1 : 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private byte[] configuration() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC, "android/content/res/Configuration", null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private byte[] patch(byte[] input) {
        ClassWriter output = new ClassWriter(0);
        new ClassReader(input).accept(new PlatformApiVisitor(output), 0);
        return output.toByteArray();
    }

    @Test public void transformedFontReadRunsOnFrameworkWithoutField() throws Exception {
        FixtureLoader loader = new FixtureLoader();
        Class<?> config = loader.define(configuration());
        Class<?> fixture = loader.define(patch(caller(true)));
        assertEquals(0, fixture.getMethod("read", config).invoke(null, config.getConstructor().newInstance()));
    }

    @Test public void unpatchedFontFixtureReproducesReportedError() throws Exception {
        FixtureLoader loader = new FixtureLoader();
        Class<?> config = loader.define(configuration());
        Class<?> fixture = loader.define(caller(true));
        try {
            fixture.getMethod("read", config).invoke(null, config.getConstructor().newInstance());
            fail("Fixture must reproduce missing platform field");
        } catch (InvocationTargetException error) {
            assertTrue(error.getCause() instanceof NoSuchFieldError);
        }
    }

    @Test public void transformedOverlayReadPreservesOtherInsetBits() throws Exception {
        Class<?> fixture = new FixtureLoader().define(patch(caller(false)));
        assertEquals(3, fixture.getMethod("read").invoke(null));
    }

    @Test(expected = IllegalStateException.class)
    public void changedLibraryTargetFailsInsteadOfSilentlySkippingPatch() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC, "ChangedHelper", null, "java/lang/Object", null);
        writer.visitEnd();
        patch(writer.toByteArray());
    }

    private static class FixtureLoader extends ClassLoader {
        FixtureLoader() { super(PlatformApiVisitorTest.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
}
