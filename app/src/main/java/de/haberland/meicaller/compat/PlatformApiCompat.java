package de.haberland.meicaller.compat;

import java.lang.reflect.InvocationTargetException;

/**
 * Checks actual public platform members, not just SDK_INT. Some reported runtimes
 * enter AndroidX's API 31/34 paths without providing the corresponding members.
 * Called from two narrowly instrumented AndroidX helpers; see buildSrc.
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
