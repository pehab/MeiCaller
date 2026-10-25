package de.meiapps.updates;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Source-aware, consent-based APK updates. Play installations never enter the GitHub path. */
// Shared across apps with minSdk 26–31; keep their compatibility branches.
@android.annotation.SuppressLint("ObsoleteSdkInt")
public final class GitHubUpdates implements Application.ActivityLifecycleCallbacks {
    private static final Map<Activity, GitHubUpdates> INSTANCES = new WeakHashMap<>();
    private final Activity activity;
    private final String repository, feed;
    private final Runnable beforeInstall;
    private final SharedPreferences preferences;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean resumed, destroyed, busy;
    private Runnable pending;

    public static GitHubUpdates attach(Activity activity, String repository, Runnable beforeInstall) {
        GitHubUpdates current = INSTANCES.get(activity);
        if (current != null) return current;
        GitHubUpdates controller = new GitHubUpdates(activity, repository, beforeInstall);
        INSTANCES.put(activity, controller);
        activity.getApplication().registerActivityLifecycleCallbacks(controller);
        return controller;
    }
    private GitHubUpdates(Activity activity, String repository, Runnable beforeInstall) {
        if (!repository.matches("pehab/[A-Za-z0-9]+")) throw new IllegalArgumentException("Unexpected repository");
        this.activity = activity; this.repository = repository; this.beforeInstall = beforeInstall;
        String name = repository.substring(repository.indexOf('/') + 1);
        feed = "https://github.com/" + repository + "/releases/download/"
                + ("MeiHotel".equals(name) ? "preview/MeiHotel-update.json" : "updates/" + name + "-update.json");
        preferences = activity.getSharedPreferences("updates", Context.MODE_PRIVATE);
    }
    public static void showSettings(Context context, String repository) {
        Context current = context;
        while (current instanceof ContextWrapper && !(current instanceof Activity)) {
            Context base = ((ContextWrapper) current).getBaseContext(); if (base == current) break; current = base;
        }
        if (current instanceof Activity) attach((Activity) current, repository, () -> { }).settings();
    }
    public boolean automatic() { return UpdateSource.get(activity) == UpdatePolicy.Source.GITHUB && preferences.getBoolean("automatic", true); }
    public boolean fromPlay() { return UpdateSource.isPlay(activity); }
    public void toggle() {
        if (fromPlay()) { openPlay(activity); return; }
        if (UpdateSource.get(activity) != UpdatePolicy.Source.GITHUB) { message("Installationsquelle konnte nicht erkannt werden."); return; }
        preferences.edit().putBoolean("automatic", !automatic()).apply();
    }
    public void settings() {
        resumed = true;
        if (fromPlay()) {
            new AlertDialog.Builder(activity).setTitle("Updates über Google Play")
                    .setMessage("Diese App wurde über Google Play installiert. Updates erhältst du dort.")
                    .setPositiveButton("Google Play öffnen", (d, n) -> openPlay(activity)).setNegativeButton("Schließen", null).show();
            return;
        }
        if (UpdateSource.get(activity) != UpdatePolicy.Source.GITHUB) { message("Installationsquelle konnte nicht erkannt werden."); return; }
        boolean automatic = automatic();
        new AlertDialog.Builder(activity).setTitle("Updates über GitHub")
                .setItems(new String[]{"Jetzt nach Updates suchen", "Tägliche Prüfung: " + (automatic ? "an" : "aus")}, (d, n) -> {
                    if (n == 0) check(true); else { toggle(); message(automatic() ? "Tägliche Prüfung eingeschaltet" : "Tägliche Prüfung ausgeschaltet"); }
                }).setNegativeButton("Schließen", null).show();
    }
    public static void openPlay(Activity activity) {
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + activity.getPackageName())).setPackage("com.android.vending")); }
        catch (ActivityNotFoundException | SecurityException e) {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + activity.getPackageName()))); }
            catch (ActivityNotFoundException | SecurityException unavailable) { Toast.makeText(activity, "Google Play konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show(); }
        }
    }
    public void resume() {
        if (destroyed) return; resumed = true;
        if (UpdateSource.get(activity) != UpdatePolicy.Source.GITHUB) { pending = null; preferences.edit().remove("waitingPermission").apply(); return; }
        if (pending != null) { Runnable task = pending; pending = null; task.run(); return; }
        if (preferences.getBoolean("waitingPermission", false)) {
            preferences.edit().remove("waitingPermission").apply();
            try { install(parse(preferences.getString("candidate", ""))); }
            catch (JSONException | IllegalArgumentException e) { message("Bitte erneut nach Updates suchen."); }
            return;
        }
        long now = System.currentTimeMillis(), last = preferences.getLong("lastCheck", 0);
        if (automatic() && (now - last >= 86400000L || last > now)) check(false);
    }
    public void pause() { resumed = false; }
    public void destroy() {
        if (destroyed) return; destroyed = true; pending = null; worker.shutdownNow();
        activity.getApplication().unregisterActivityLifecycleCallbacks(this); INSTANCES.remove(activity);
    }
    private void ui(Runnable task) {
        activity.runOnUiThread(() -> { if (destroyed || activity.isFinishing() || activity.isDestroyed()) return; if (resumed) task.run(); else pending = task; });
    }
    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    public void check(boolean manual) {
        if (destroyed) return;
        UpdatePolicy.Source source = UpdateSource.get(activity);
        if (source != UpdatePolicy.Source.GITHUB) { if (manual) { if (source == UpdatePolicy.Source.PLAY) openPlay(activity); else message("Installationsquelle konnte nicht erkannt werden."); } return; }
        if (busy) { if (manual) message("Eine Update-Prüfung oder ein Download läuft bereits."); return; }
        busy = true; preferences.edit().putLong("lastCheck", System.currentTimeMillis()).apply();
        if (manual) message("Suche nach Updates …");
        worker.execute(() -> {
            try {
                HttpURLConnection connection = open(new URL(feed)); String data;
                try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int n;
                    while ((n = in.read(buffer)) != -1) { if (out.size() + n > 65536) throw new IOException("Metadata too large"); out.write(buffer, 0, n); }
                    data = new String(out.toByteArray(), StandardCharsets.UTF_8);
                } finally { connection.disconnect(); }
                UpdateMetadata info = parse(data);
                ui(() -> { busy = false; if (UpdateSource.get(activity) != UpdatePolicy.Source.GITHUB) return;
                    if (!info.newerThan(installedCode())) { if (manual) message("Du hast die aktuelle Version."); return; }
                    new AlertDialog.Builder(activity).setTitle("Update " + info.name + " verfügbar")
                            .setMessage("Die APK wird vor der Installation geprüft. Deine Daten bleiben bei der Aktualisierung erhalten.")
                            .setNegativeButton("Später", null).setPositiveButton("Herunterladen", (d, n) -> download(info)).show();
                });
            } catch (IOException | JSONException | IllegalArgumentException e) { ui(() -> { busy = false; if (manual) message("Updates gerade nicht erreichbar. Bitte später erneut versuchen."); }); }
        });
    }
    private UpdateMetadata parse(String json) throws JSONException {
        JSONObject object = new JSONObject(json); if (object.getInt("schema") != 1) throw new JSONException("Unknown schema");
        return new UpdateMetadata(repository, object.getLong("versionCode"), object.getString("versionName"), object.getString("url"), object.getString("sha256"), object.getLong("size"));
    }
    private String encode(UpdateMetadata info) throws JSONException {
        return new JSONObject().put("schema", 1).put("versionCode", info.code).put("versionName", info.name)
                .put("url", info.url).put("sha256", info.sha256).put("size", info.size).toString();
    }
    private File apk() { return new File(activity.getCacheDir(), "github-update.apk"); }
    private long installedCode() {
        try { return code(activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0)); }
        catch (PackageManager.NameNotFoundException e) { return Long.MAX_VALUE; }
    }
    private void download(UpdateMetadata info) {
        if (busy || destroyed || UpdateSource.get(activity) != UpdatePolicy.Source.GITHUB) return;
        busy = true; message("Update wird heruntergeladen …");
        worker.execute(() -> {
            File part = new File(activity.getCacheDir(), "github-update.part");
            try {
                HttpURLConnection connection = open(new URL(info.url));
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buffer = new byte[16384]; int n; long total = 0;
                    while ((n = in.read(buffer)) != -1) { total += n; if (Thread.currentThread().isInterrupted() || total > info.size) throw new IOException("Download interrupted or too large"); out.write(buffer, 0, n); }
                    out.getFD().sync();
                } finally { connection.disconnect(); }
                verify(info, part);
                if (!part.renameTo(apk())) throw new IOException("Cannot commit APK");
                if (!preferences.edit().putString("candidate", encode(info)).commit()) throw new IOException("Cannot persist update");
                ui(() -> { busy = false; if (UpdateSource.get(activity) != UpdatePolicy.Source.GITHUB) return;
                    new AlertDialog.Builder(activity).setTitle("Update bereit")
                            .setMessage(declaresInstaller() ? "Version " + info.name + " wurde geprüft. Jetzt installieren?" : "Version " + info.name + " wurde geprüft. Die Installation erfolgt über deinen Browser.")
                            .setNegativeButton("Später", null).setPositiveButton(declaresInstaller() ? "Installieren" : "Im Browser laden", (d, n) -> install(info)).show();
                });
            } catch (IOException | JSONException | PackageManager.NameNotFoundException e) { ui(() -> { busy = false; message("Das Update konnte nicht sicher geladen werden. Bitte erneut versuchen."); }); }
            finally { if (part.exists() && !part.delete()) part.deleteOnExit(); }
        });
    }
    private void verify(UpdateMetadata info, File file) throws IOException, PackageManager.NameNotFoundException {
        if (!info.newerThan(installedCode()) || !info.matches(file)) throw new IOException("Invalid update bytes or version");
        verifyArchive(activity, file, info.code, info.name);
    }
    @SuppressWarnings("deprecation")
    public static void verifyArchive(Context context, File file, long expectedCode, String expectedName) throws IOException, PackageManager.NameNotFoundException {
        PackageManager manager = context.getPackageManager(); int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo current = manager.getPackageInfo(context.getPackageName(), flags), archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (archive == null || !context.getPackageName().equals(archive.packageName) || code(archive) != expectedCode || !expectedName.equals(archive.versionName)
                || signatures(current).isEmpty() || !signatures(current).equals(signatures(archive))) throw new IOException("APK identity or signing certificate mismatch");
    }
    @SuppressWarnings("deprecation")
    private static Set<String> signatures(PackageInfo info) {
        Signature[] values = Build.VERSION.SDK_INT >= 28 ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners() : info.signatures;
        Set<String> result = new HashSet<>(); if (values != null) for (Signature value : values) result.add(value.toCharsString()); return result;
    }
    @SuppressWarnings("deprecation")
    private static long code(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    private boolean declaresInstaller() { return supportsDirectInstall(activity); }
    public static boolean supportsDirectInstall(Context context) {
        try { String[] permissions = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            return permissions != null && Arrays.asList(permissions).contains("android.permission.REQUEST_INSTALL_PACKAGES"); }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }
    private void install(UpdateMetadata info) {
        if (busy || destroyed || UpdateSource.get(activity) != UpdatePolicy.Source.GITHUB) return;
        busy = true;
        worker.execute(() -> {
            try { verify(info, apk()); ui(() -> { busy = false;
                if (UpdateSource.get(activity) != UpdatePolicy.Source.GITHUB) return;
                try {
                    if (!declaresInstaller()) { beforeInstall.run(); activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(info.url))); return; }
                    if (!activity.getPackageManager().canRequestPackageInstalls()) {
                        new AlertDialog.Builder(activity).setTitle("Installation erlauben").setMessage("Android benötigt einmalig die Freigabe „Aus dieser Quelle zulassen“ für diese App.")
                                .setNegativeButton("Später", null).setPositiveButton("Android-Einstellungen", (d, n) -> {
                                    preferences.edit().putBoolean("waitingPermission", true).apply();
                                    try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); }
                                    catch (ActivityNotFoundException | SecurityException e) { preferences.edit().remove("waitingPermission").apply(); message("Installationsfreigabe konnte nicht geöffnet werden."); }
                                }).show(); return;
                    }
                    beforeInstall.run(); activity.startActivity(installIntent(activity));
                } catch (RuntimeException e) { message("Android konnte die Installation nicht öffnen."); }
            }); } catch (IOException | PackageManager.NameNotFoundException e) { ui(() -> { busy = false; message("Die Update-Datei fehlt oder ist ungültig. Bitte erneut nach Updates suchen."); }); }
        });
    }
    public static Intent installIntent(Context context) {
        return new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://" + context.getPackageName() + ".githubUpdates/github-update.apk"), "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    private static HttpURLConnection open(URL url) throws IOException {
        for (int redirects = 0; redirects < 7; redirects++) {
            String host = url.getHost();
            if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null || url.getPort() != -1 || !("github.com".equals(host) || host.endsWith(".githubusercontent.com"))) throw new IOException("Untrusted redirect");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(); connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setConnectTimeout(10000); connection.setReadTimeout(20000); connection.setRequestProperty("User-Agent", "MeiApps-Android-Updater");
            int status = connection.getResponseCode(); if (status == 200) return connection;
            String location = connection.getHeaderField("Location"); connection.disconnect();
            if ((status == 301 || status == 302 || status == 303 || status == 307 || status == 308) && location != null) { url = new URL(url, location); continue; }
            throw new IOException("HTTP " + status);
        }
        throw new IOException("Too many redirects");
    }
    @Override public void onActivityResumed(Activity current) { if (current == activity) resume(); }
    @Override public void onActivityPaused(Activity current) { if (current == activity) pause(); }
    @Override public void onActivityDestroyed(Activity current) { if (current == activity) destroy(); }
    @Override public void onActivityCreated(Activity current, Bundle state) { }
    @Override public void onActivityStarted(Activity current) { }
    @Override public void onActivityStopped(Activity current) { }
    @Override public void onActivitySaveInstanceState(Activity current, Bundle state) { }
}
