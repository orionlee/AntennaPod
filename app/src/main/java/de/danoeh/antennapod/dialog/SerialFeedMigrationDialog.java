package de.danoeh.antennapod.dialog;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

public class SerialFeedMigrationDialog {
    public static void check(@NonNull Context context) {
        if (false) { // TODO-1077: true if migration has not been done, and there are serial podcasts
            new AlertDialog.Builder(context)
                    .setTitle("Serial podcasts detected") // TODO-1077: i18n
                    .setMessage("AntennaPod now have better support for serial podcasts, e.g., automatically download the oldest episode first.\n\nTap Migrate to start the migration.")
                    .setNegativeButton("No", null)
                    .setNeutralButton("Not now", null)
                    .setPositiveButton("Migrate...", null)
                    .show();
        }
    }
}
