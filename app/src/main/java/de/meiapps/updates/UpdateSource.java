package de.meiapps.updates;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

// Shared across apps with minSdk 26–31; keep their compatibility branches.
@android.annotation.SuppressLint("ObsoleteSdkInt")
public final class UpdateSource {
    private UpdateSource() { }
    public static UpdatePolicy.Source get(Context context) {
        try {
            String installer = Build.VERSION.SDK_INT >= 30
                    ? context.getPackageManager().getInstallSourceInfo(context.getPackageName()).getInstallingPackageName()
                    : legacyInstaller(context);
            return UpdatePolicy.select(installer, true);
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException | SecurityException e) {
            // A failed provenance query must never turn a Play installation into a GitHub one.
            return UpdatePolicy.select(null, false);
        }
    }
    public static boolean isPlay(Context context) { return get(context) == UpdatePolicy.Source.PLAY; }
    @SuppressWarnings("deprecation")
    private static String legacyInstaller(Context context) {
        return context.getPackageManager().getInstallerPackageName(context.getPackageName());
    }
}
