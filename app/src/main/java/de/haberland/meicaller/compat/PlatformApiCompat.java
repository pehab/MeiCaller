package de.haberland.meicaller.compat;

import java.lang.reflect.InvocationTargetException;

/**
 * Checks actual public platform members, not just SDK_INT. Some reported runtimes
 * enter AndroidX's API 31/34 paths without providing the corresponding members.
 * Called from narrowly instrumented AndroidX access sites; see buildSrc.
 */
public final class PlatformApiCompat {
    private PlatformApiCompat() {}

    public static int fontWeightAdjustment(Object configuration) {
        try {
            return configuration.getClass().getField("fontWeightAdjustment").getInt(configuration);
        } catch (NoSuchFieldException missing) {
            // No user weight adjustment is available on this framework.
            return 0;
        } catch (IllegalAccessException inaccessible) {
            throw new IllegalStateException("Cannot read public font weight adjustment", inaccessible);
        }
    }

    public static int systemOverlays() {
        return systemOverlays(InsetsTypeHolder.TYPE);
    }

    static int systemOverlays(Class<?> type) {
        if (type == null) return 0;
        try {
            return (Integer) type.getMethod("systemOverlays").invoke(null);
        } catch (NoSuchMethodException missing) {
            // An absent overlay type contributes no bits; other requested types remain intact.
            return 0;
        } catch (IllegalAccessException inaccessible) {
            throw new IllegalStateException("Cannot read public system overlay type", inaccessible);
        } catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("System overlay lookup failed", cause);
        }
    }

    public static void setViewTranslationCallback(Object unusedHelper, Object view) {
        invokeTranslation("setViewTranslationCallback", view);
    }

    public static void clearViewTranslationCallback(Object unusedHelper, Object view) {
        invokeTranslation("clearViewTranslationCallback", view);
    }

    private static void invokeTranslation(String method, Object view) {
        ClassLoader loader = PlatformApiCompat.class.getClassLoader();
        invokeTranslation(loader, method, view);
    }

    static void invokeTranslation(ClassLoader loader, String method, Object view) {
        if (!supportsViewTranslation(loader)) return;
        invokeTranslationHelper(loader, method, view);
    }

    static boolean supportsViewTranslation(ClassLoader loader) {
        try {
            Class<?> callback = Class.forName("android.view.translation.ViewTranslationCallback", false, loader);
            Class<?> view = Class.forName("android.view.View", false, loader);
            view.getMethod("setViewTranslationCallback", callback);
            view.getMethod("clearViewTranslationCallback");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            return false;
        }
    }

    static void invokeTranslationHelper(ClassLoader loader, String method, Object view) {
        try {
            Class<?> helper = Class.forName(
                    "androidx.compose.ui.platform.AndroidComposeViewTranslationCallbackS", true, loader);
            java.lang.reflect.Field instance = helper.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            java.lang.reflect.Method callback = helper.getDeclaredMethod(method,
                    Class.forName("android.view.View", false, loader));
            callback.setAccessible(true);
            callback.invoke(instance.get(null), view);
        } catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Compose translation callback failed", cause);
        } catch (ReflectiveOperationException changedHelper) {
            throw new IllegalStateException("Compose translation helper changed", changedHelper);
        }
    }

    private static final class InsetsTypeHolder {
        private static final Class<?> TYPE = findType();

        private static Class<?> findType() {
            try {
                return Class.forName("android.view.WindowInsets$Type");
            } catch (ClassNotFoundException missing) {
                return null;
            }
        }
    }
}
