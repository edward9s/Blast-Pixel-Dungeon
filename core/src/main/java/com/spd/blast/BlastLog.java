package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.watabou.utils.FileUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Per-save diagnostic text, included by the existing save export. */
public final class BlastLog {
    private static final String FILE_NAME = "blast-debug.txt";
    private static final long MAX_BYTES = 1024 * 1024;

    private static int slot = -1;
    private static BufferedWriter writer;
    private static long size;
    private static boolean opened;
    private static boolean failed;

    private BlastLog() {}

    /** A new entry into a save replaces its previous diagnostic file. */
    public static synchronized void beginGame(int saveSlot) {
        if (saveSlot <= 0) throw new IllegalArgumentException("Invalid save slot: " + saveSlot);
        close();
        slot = saveSlot;
        opened = false;
        failed = false;
        if (BlastSettings.debugLog()) {
            try {
                open(false);
            } catch (RuntimeException error) {
                report(error);
            }
        }
    }

    /** The setting may be toggled off without losing the current session. */
    public static synchronized void stop() {
        close();
    }

    public static synchronized void debug(String message) {
        if (!BlastSettings.debugLog() || slot < 0 || failed) return;
        try {
            if (writer == null) open(opened);
            String line = "[Blast] " + message + "\n";
            int bytes = line.getBytes(StandardCharsets.UTF_8).length;
            if (size + bytes > MAX_BYTES) {
                close();
                open(false);
            }
            writer.write(line);
            writer.flush();
            size += bytes;
        } catch (IOException | RuntimeException error) {
            report(error);
        }
    }

    private static void open(boolean append) {
        String path = GamesInProgress.gameFolder(slot) + "/" + FILE_NAME;
        com.badlogic.gdx.files.FileHandle file = FileUtils.getFileHandle(path);
        writer = new BufferedWriter(new OutputStreamWriter(
                file.write(append), StandardCharsets.UTF_8));
        size = append ? file.length() : 0;
        opened = true;
    }

    private static void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException error) {
                System.err.println("Blast debug close failed: " + error);
            }
        }
        writer = null;
    }

    private static void report(Exception error) {
        close();
        failed = true;
        System.err.println("Blast debug write failed: " + error);
    }
}
