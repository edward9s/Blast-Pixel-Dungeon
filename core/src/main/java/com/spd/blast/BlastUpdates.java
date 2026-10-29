package com.spd.blast;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches published Blast versions without blocking the game loop. */
public final class BlastUpdates {
    private static final String RELEASES_URL =
            "https://api.github.com/repos/edward9s/Blast-Pixel-Dungeon/releases?per_page=30";
    private static final long CHECK_INTERVAL_MS = 60L * 60L * 1000L;
    private static final Pattern RELEASE_TAG = Pattern.compile(
            "Blast-v([0-9]+(?:\\.[0-9]+)*)-SPD-v[0-9]+(?:\\.[0-9]+)*");

    private static boolean checking;
    private static long lastSuccessfulCheck;
    private static String latestVersion;

    private BlastUpdates() {}

    public static synchronized void checkForUpdate() {
        long now = System.currentTimeMillis();
        if (checking || (lastSuccessfulCheck != 0L
                && now >= lastSuccessfulCheck
                && now - lastSuccessfulCheck < CHECK_INTERVAL_MS)) {
            return;
        }

        checking = true;
        try {
            Thread worker = new Thread(BlastUpdates::checkRemote, "Blast-Update-Check");
            worker.setDaemon(true);
            worker.start();
        } catch (RuntimeException e) {
            checking = false;
            throw e;
        }
    }

    private static void checkRemote() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "Blast-Pixel-Dungeon");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Blast release check returned HTTP "
                        + connection.getResponseCode());
            }

            String newest;
            try (InputStream stream = connection.getInputStream()) {
                newest = newestReleaseVersion(new JsonReader().parse(stream));
            }

            String update = newest != null
                    && compareVersions(newest, BlastVersion.VERSION) > 0 ? newest : null;
            synchronized (BlastUpdates.class) {
                latestVersion = update;
                lastSuccessfulCheck = System.currentTimeMillis();
            }
        } catch (IOException | RuntimeException e) {
            // Keep the previous notice, and retry on a subsequent menu opening.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            synchronized (BlastUpdates.class) {
                checking = false;
            }
        }
    }

    public static synchronized String latestVersion() {
        return latestVersion;
    }

    static String newestReleaseVersion(JsonValue releases) {
        if (releases == null || !releases.isArray()) {
            throw new IllegalArgumentException("GitHub releases must be a JSON array");
        }
        String newest = null;
        for (JsonValue release = releases.child; release != null; release = release.next) {
            if (release.getBoolean("draft", false)
                    || release.getBoolean("prerelease", false)) {
                continue;
            }
            Matcher match = RELEASE_TAG.matcher(release.getString("tag_name", ""));
            if (!match.matches()) {
                continue;
            }
            String candidate = match.group(1);
            if (newest == null || compareVersions(candidate, newest) > 0) {
                newest = candidate;
            }
        }
        return newest;
    }

    static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}
