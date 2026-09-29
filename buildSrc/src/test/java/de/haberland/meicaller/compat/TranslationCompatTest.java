package de.haberland.meicaller.compat;

import org.junit.Test;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

public class TranslationCompatTest implements Opcodes {
    private static final String VIEW = "android/view/View";
    private static final String CALLBACK = "android/view/translation/ViewTranslationCallback";
    private static final String HELPER = "androidx/compose/ui/platform/AndroidComposeViewTranslationCallbackS";

    @Test public void missingPlatformClassSkipsOptionalTranslation() {
        ClassLoader loader = getClass().getClassLoader();
        assertFalse(PlatformApiCompat.supportsViewTranslation(loader));
        PlatformApiCompat.invokeTranslation(loader, "setViewTranslationCallback", new Object());
        PlatformApiCompat.invokeTranslation(loader, "clearViewTranslationCallback", new Object());
    }

    @Test public void missingPlatformMethodsSkipHelperLoading() throws Exception {
        Loader loader = framework(false, false);
        assertFalse(PlatformApiCompat.supportsViewTranslation(loader));
        PlatformApiCompat.invokeTranslation(loader, "setViewTranslationCallback", loader.view.getConstructor().newInstance());
    }

    @Test public void supportedPlatformPreservesOriginalAttachAndDetach() throws Exception {
        Loader loader = framework(true, false);
        assertTrue(PlatformApiCompat.supportsViewTranslation(loader));
        Object view = loader.view.getConstructor().newInstance();
        PlatformApiCompat.invokeTranslation(loader, "setViewTranslationCallback", view);
        assertEquals(1, loader.view.getField("calls").getInt(view));
        PlatformApiCompat.invokeTranslation(loader, "clearViewTranslationCallback", view);
        assertEquals(2, loader.view.getField("calls").getInt(view));
    }

    @Test(expected = IllegalStateException.class)
    public void unrelatedHelperFailureStillPropagates() throws Exception {
        Loader loader = framework(true, true);
        PlatformApiCompat.invokeTranslation(loader, "setViewTranslationCallback", loader.view.getConstructor().newInstance());
    }

    private Loader framework(boolean methods, boolean fail) {
        Loader loader = new Loader();
        ClassWriter callback = new ClassWriter(0);
        callback.visit(V1_8, ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE, CALLBACK, null, "java/lang/Object", null);
        callback.visitEnd();
        loader.define(callback.toByteArray());
        ClassWriter view = object(VIEW);
        view.visitField(ACC_PUBLIC, "calls", "I", null, null).visitEnd();
        if (methods) {
            empty(view, "setViewTranslationCallback", "(L" + CALLBACK + ";)V");
            empty(view, "clearViewTranslationCallback", "()V");
        }
        view.visitEnd();
        loader.view = loader.define(view.toByteArray());
        if (!methods) return loader; // absent helper must never be resolved
        ClassWriter helper = object(HELPER);
        helper.visitField(ACC_PUBLIC | ACC_STATIC, "INSTANCE", "L" + HELPER + ";", null, null).visitEnd();
        MethodVisitor init = helper.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        init.visitCode();
        init.visitTypeInsn(NEW, HELPER); init.visitInsn(DUP);
        init.visitMethodInsn(INVOKESPECIAL, HELPER, "<init>", "()V", false);
        init.visitFieldInsn(PUTSTATIC, HELPER, "INSTANCE", "L" + HELPER + ";");
        init.visitInsn(RETURN); init.visitMaxs(2, 0); init.visitEnd();
        for (String name : new String[]{"setViewTranslationCallback", "clearViewTranslationCallback"}) {
            MethodVisitor m = helper.visitMethod(ACC_PUBLIC, name, "(L" + VIEW + ";)V", null, null);
            m.visitCode();
            if (fail) {
                m.visitTypeInsn(NEW, "java/lang/IllegalStateException"); m.visitInsn(DUP);
                m.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false);
                m.visitInsn(ATHROW);
            } else {
                m.visitVarInsn(ALOAD, 1); m.visitInsn(DUP);
                m.visitFieldInsn(GETFIELD, VIEW, "calls", "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD);
                m.visitFieldInsn(PUTFIELD, VIEW, "calls", "I"); m.visitInsn(RETURN);
            }
            m.visitMaxs(3, 2); m.visitEnd();
        }
        helper.visitEnd(); loader.define(helper.toByteArray());
        return loader;
    }

    private ClassWriter object(String name) {
        ClassWriter w = new ClassWriter(0);
        w.visit(V1_8, ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(1, 1); m.visitEnd();
        return w;
    }

    private void empty(ClassWriter w, String name, String desc) {
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, name, desc, null, null);
        m.visitCode(); m.visitInsn(RETURN); m.visitMaxs(0, 2); m.visitEnd();
    }

    private static final class Loader extends ClassLoader {
        Class<?> view;
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
}
