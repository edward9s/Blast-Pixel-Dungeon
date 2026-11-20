package com.spd.blast;

import com.watabou.utils.DeviceCompat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Opt-in diagnostic text files readable through the device's Documents folder. */
public final class BlastLog {
    private static final String DIRECTORY = "Documents/Blast Pixel Dungeon/";
    private static final int MAX_FILE_BYTES = 1024 * 1024;

    private static BufferedWriter writer;
    private static String location;
    private static int bytesWritten;
    private static int sequence;
    private static boolean writeFailed;

    private BlastLog() {}

    /** Start a new file on the first enabled event, or when Debug Log is enabled. */
    public static synchronized String start() throws IOException {
        if (writer != null) return location;

        String name = "blast-debug-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date())
                + "-" + sequence++ + ".txt";
        OutputStream stream;
        if (DeviceCompat.isAndroid()) {
            stream = androidStream(name);
        } else if (DeviceCompat.isDesktop()) {
            File directory = new File(System.getProperty("user.home"), DIRECTORY);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create debug directory: " + directory);
            }
            stream = new FileOutputStream(new File(directory, name));
        } else {
            throw new IOException("Unsupported platform for debug file");
        }

        writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
        location = DIRECTORY + name;
        bytesWritten = 0;
        writeFailed = false;
        return location;
    }

    /** Called when the setting is turned off; each session keeps its own file. */
    public static synchronized void stop() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException error) {
                System.err.println("Blast debug file close failed: " + error);
            }
        }
        writer = null;
        location = null;
        bytesWritten = 0;
        writeFailed = false;
    }

    public static synchronized void debug(String message) {
        if (!BlastSettings.debugLog() || writeFailed) return;
        String line = "[Blast] " + message + "\n";
        try {
            if (writer == null) start();
            int length = line.getBytes(StandardCharsets.UTF_8).length;
            if (bytesWritten + length > MAX_FILE_BYTES) {
                stop();
                start();
            }
            writer.write(line);
            writer.flush();
            bytesWritten += length;
        } catch (IOException error) {
            System.err.println("Blast debug file write failed: " + error);
            stop();
            writeFailed = true;
        }
    }

    /** Android 10+: create a text file in shared Documents without file-manager permission. */
    private static OutputStream androidStream(String name) throws IOException {
        try {
            Class<?> version = Class.forName("android.os.Build$VERSION");
            if (version.getField("SDK_INT").getInt(null) < 29) {
                throw new IOException("Shared debug files require Android 10 or newer");
            }

            Object context = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (context == null) throw new IOException("Android context unavailable");

            Class<?> valuesType = Class.forName("android.content.ContentValues");
            Object values = valuesType.getConstructor().newInstance();
            valuesType.getMethod("put", String.class, String.class)
                    .invoke(values, "_display_name", name);
            valuesType.getMethod("put", String.class, String.class)
                    .invoke(values, "mime_type", "text/plain");
            valuesType.getMethod("put", String.class, String.class)
                    .invoke(values, "relative_path", DIRECTORY);

            Class<?> uriType = Class.forName("android.net.Uri");
            Object collection = Class.forName("android.provider.MediaStore$Files")
                    .getMethod("getContentUri", String.class)
                    .invoke(null, "external_primary");
            Object resolver = context.getClass().getMethod("getContentResolver").invoke(context);
            Object uri = resolver.getClass().getMethod("insert", uriType, valuesType)
                    .invoke(resolver, collection, values);
            if (uri == null) throw new IOException("MediaStore rejected debug file creation");
            Object output = resolver.getClass().getMethod("openOutputStream", uriType)
                    .invoke(resolver, uri);
            if (!(output instanceof OutputStream)) {
                throw new IOException("MediaStore returned no debug file stream");
            }
            return (OutputStream) output;
        } catch (IOException error) {
            throw error;
        } catch (ReflectiveOperationException error) {
            throw new IOException("Cannot create Android debug file", error);
        }
    }
}
