package de.meiapps.updates;

/** Installation provenance, independent of build type or the originating download app. */
public final class UpdatePolicy {
    private UpdatePolicy() { }
    public enum Source { PLAY, GITHUB, UNAVAILABLE }
    public static Source select(String installingPackage, boolean readable) {
        if (!readable) return Source.UNAVAILABLE;
        return "com.android.vending".equals(installingPackage) ? Source.PLAY : Source.GITHUB;
    }
}
