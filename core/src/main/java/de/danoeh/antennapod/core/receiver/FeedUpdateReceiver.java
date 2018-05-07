package de.danoeh.antennapod.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.util.LogToFile;
import de.danoeh.antennapod.core.util.NetworkUtils;

/**
 * Refreshes all feeds when it receives an intent
 */
public class FeedUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "FeedUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        LogToFile.d(context, TAG, "Received intent");
        ClientConfig.initialize(context);
        if (NetworkUtils.networkAvailable() && NetworkUtils.isDownloadAllowed()) {
            LogToFile.d(context, TAG, "Automatic feed update: starting, network available");
            DBTasks.refreshAllFeeds(context, null);
        } else {
            LogToFile.d(context, TAG, "Blocking automatic update: no wifi available / no mobile updates allowed");
        }
        UserPreferences.restartUpdateAlarm(false);
    }

}
