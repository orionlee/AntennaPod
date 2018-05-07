package de.danoeh.antennapod.core.util;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.util.Calendar;

import de.danoeh.antennapod.core.BuildConfig;

/**
 * Utility to log the requested message to an external log files for debugging purpose.
 * It is useful for
 * 1) debugging relesae build (with logcat log stripped),
 * 2) cases where logcat seems to be unreliable
 */
public class LogToFile implements Closeable {

    public static final String TAG = "LogToFile";

    @NonNull
    private final Context mCtx;
    private final Writer mLogWriter;

    /**
     * Requires WRITE_EXTERNAL_STORAGE permission be granted by caller.
     */
    public LogToFile(@NonNull Context ctx) {
        mCtx = ctx;
        mLogWriter = createLogWriter();
    }

    public static void d(@NonNull Context context, @NonNull String tag, @NonNull String msg) {
        Log.d(tag, msg); // Standard logcat log

        try ( LogToFile logToFile = new LogToFile(context)) {
            logToFile.d(tag, msg);
        } catch (IOException e) {
            reportInternalError(context, e, "Error in closing Log File");
        }
    }

    public void d(@NonNull String tag, @NonNull String msg) {
        String level = "D";
        String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG)
                .format(Calendar.getInstance().getTime());

        String logLine = String.format("%s  %s/%s  %s", timestamp, level, tag, msg);

        println(logLine);
    }

    private void println(String logLine) {
        if (mLogWriter == null) {
            return;
        }

        try {
            /// Log.v(TAG, "[" + logLine + "]");
            // Ensure writing the line is atomic across
            // the entire application process
            synchronized (LogToFile.class) {
                mLogWriter.write(logLine);
                mLogWriter.write("\n");
                mLogWriter.flush();
            }
        } catch (Throwable t) {
            reportInternalError(t, "Error in writing to log file");
        }
    }

    @Override
    public void close() throws IOException {
        mLogWriter.close();
    }

    private Writer createLogWriter() {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            boolean dirCreated = dir.mkdirs();
            File logFile = new File(dir, BuildConfig.APPLICATION_ID + "-" + BuildConfig.BUILD_TYPE + ".log");
            boolean fileCreated = logFile.createNewFile();

            return new FileWriter(logFile, true); // mark writer as append
        } catch (Throwable t) {
            reportInternalError(t, "Error in creating / accessing log file");
        }
        return null;
    }

    private void reportInternalError(@NonNull Throwable t, @NonNull String errMsg) {
        reportInternalError(mCtx, t, errMsg);
    }

    private static void reportInternalError(@NonNull Context context, @NonNull Throwable t, @NonNull String errMsg) {
        Log.e(TAG, errMsg, t);
        Toast.makeText(context, String.format("Error: %s | %s: %s",
                errMsg, t.getClass().getSimpleName(),
                t.getMessage())
                , Toast.LENGTH_LONG);
    }

}
