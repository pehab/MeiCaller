package de.meiapps.updates;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class UpdateMetadata {
    final long code, size;
    final String name, url, sha256;
    UpdateMetadata(String repository, long code, String name, String url, String hash, long size) {
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || code <= 0 || code > Integer.MAX_VALUE
                || name == null || name.isEmpty() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl)
                || hash == null || !hash.matches("[a-f0-9]{64}") || size <= 0 || size > 250L * 1024 * 1024)
            throw new IllegalArgumentException("Invalid update metadata");
        try {
            URI parsed = new URI(url);
            String prefix = "/" + repository + "/releases/download/";
            String path = parsed.getRawPath();
            if (!"https".equals(parsed.getScheme()) || !"github.com".equals(parsed.getHost()) || parsed.getPort() != -1
                    || parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null
                    || path == null || !parsed.normalize().getRawPath().equals(path) || !path.startsWith(prefix)
                    || !path.substring(prefix.length()).matches("[A-Za-z0-9._+-]+/[A-Za-z0-9._+-]+\\.apk"))
                throw new IllegalArgumentException("Unexpected APK URL");
        } catch (URISyntaxException | NullPointerException e) { throw new IllegalArgumentException("Invalid APK URL", e); }
        this.code = code; this.name = name; this.url = url; this.sha256 = hash; this.size = size;
    }
    boolean newerThan(long current) { return code > current; }
    boolean matches(File file) throws IOException { return file.length() == size && sha256.equals(digest(file)); }
    static String digest(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[16384]; int n;
            while ((n = in.read(buffer)) != -1) hash.update(buffer, 0, n);
            StringBuilder result = new StringBuilder(64);
            for (byte b : hash.digest()) { result.append(Character.forDigit((b >> 4) & 15, 16)); result.append(Character.forDigit(b & 15, 16)); }
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
    }
}
