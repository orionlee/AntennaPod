package de.danoeh.antennapod.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.util.NetworkUtils;

/**
 * Refreshes all feeds when it receives an intent
 */
public class FeedUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "FeedUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received intent");
        ClientConfig.initialize(context);
        if (NetworkUtils.networkAvailable()) {
            refreshAllFeedsIfDownloadAllowed(context);
        } else if (NetworkUtils.networkProbablyConnected()){
            Log.d(TAG, "Workaround for #2691: Android probably incorrectly reports network disconnected. Retry after a few seconds");
            new Handler().postDelayed(()-> {
                Log.d(TAG, "  Test network again after some delay.");
                if (NetworkUtils.networkAvailable()) {
                    Log.d(TAG, "  In retry: network available. Proceed to refreshAllFeeds");
                    refreshAllFeedsIfDownloadAllowed(context);
                } else {
                    Log.d(TAG, "  In retry: network is still not available. refreshAllFeeds aborted. Details: NetworkUtils.networkProbablyConnected(): " + NetworkUtils.networkProbablyConnected());
                }
            }, 5000);
        } else {
            Log.d(TAG, "Blocking automatic update: no wifi available / no mobile updates allowed");
        }
        UserPreferences.restartUpdateAlarm(false);
    }

    private static void refreshAllFeedsIfDownloadAllowed(Context context) {
        if (NetworkUtils.isDownloadAllowed()) {
            DBTasks.refreshAllFeeds(context, null);
        }
    }

}
