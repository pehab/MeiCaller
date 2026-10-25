package de.meiapps.updates;

import java.io.*;
import java.nio.file.Files;

/** Host-side tests: real routing decisions and hostile metadata/cache inputs, no Android mocks. */
public final class UpdateContractChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    private static void rejected(String url, String hash, long size) {
        boolean rejected = false;
        try { new UpdateMetadata("pehab/MeiHotel", 5, "Prototype 20", url, hash, size); }
        catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "hostile metadata rejected: " + url);
    }
    public static void main(String[] args) throws Exception {
        check(UpdatePolicy.select("com.android.vending", true) == UpdatePolicy.Source.PLAY, "Play installs use Play");
        for (String installer : new String[]{null, "", "com.google.android.packageinstaller", "com.android.packageinstaller", "com.amazon.venezia", "adb", "com.android.vending.fake"})
            check(UpdatePolicy.select(installer, true) == UpdatePolicy.Source.GITHUB, "non-Play installer uses GitHub");
        check(UpdatePolicy.select(null, false) == UpdatePolicy.Source.UNAVAILABLE, "failed provenance lookup never enables GitHub");
        check(UpdatePolicy.select("com.android.vending", false) == UpdatePolicy.Source.UNAVAILABLE, "lookup failure cannot bypass provenance checks");
        File file = File.createTempFile("mei-update-test", ".apk");
        try {
            Files.write(file.toPath(), new byte[]{1,2,3}); String digest = UpdateMetadata.digest(file);
            String url = "https://github.com/pehab/MeiHotel/releases/download/v5/MeiHotel-5.apk";
            UpdateMetadata info = new UpdateMetadata("pehab/MeiHotel", 5, "Prototype 20", url, digest, 3);
            check(info.newerThan(4) && !info.newerThan(5) && !info.newerThan(6), "no duplicate updates or downgrades");
            check(info.matches(file), "matching bytes accepted");
            Files.write(file.toPath(), new byte[]{1,2,4}); check(!info.matches(file), "modified bytes rejected even at the same size");
            Files.write(file.toPath(), new byte[]{1}); check(!info.matches(file), "truncated APK rejected");
            for (String bad : new String[]{url.replace("https:", "http:"), url.replace("github.com", "example.com"), url.replace("MeiHotel/releases", "MeiLists/releases"), url + "?x=1", url + "#fragment", url.replace("github.com", "user@github.com"), url.replace("github.com", "github.com:443"), url.replace("/v5/", "/../v5/"), url.replace(".apk", ".zip"), url.replace("/MeiHotel/", "/%4deiHotel/")}) rejected(bad, digest, 3);
            rejected(url, "0", 3); rejected(url, digest, 0); rejected(url, digest, 251L * 1024 * 1024);
        } finally { Files.deleteIfExists(file.toPath()); }
        System.out.println("PASS: " + count + " update routing and verification checks");
    }
}
