package de.danoeh.antennapod.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.util.Log;

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

        // Experiment: always refresh, even network is not available, to see how it fares
        // against cases that network is only temporarily down.
        //
        // Still run network tests to log the network conditions.
        // For production, consider to retry upon network is available
        boolean networkAvailable = NetworkUtils.networkAvailable();
        NetworkUtils.isDownloadAllowed();
        if (true) { // Note: for production NetworkUtils.isDownloadAllowed() should always be checked.
            LogToFile.d(context, TAG, "Automatic feed update: starting, ignoring network availability");
            DBTasks.refreshAllFeeds(context, null);
        } else {
            LogToFile.d(context, TAG, "Blocking automatic update: no wifi available / no mobile updates allowed");
        }
        UserPreferences.restartUpdateAlarm(false);

        if (!networkAvailable) {
            LogToFile.d(context, TAG, "Network seems to be unavailable. Investigate if network remains disconnected in next few seconds");
            new Handler().postDelayed(NetworkUtils::networkAvailable, 2000);
            new Handler().postDelayed(NetworkUtils::networkAvailable, 4000);
            new Handler().postDelayed(NetworkUtils::networkAvailable, 6000);

            // Refresh all feeds once network is available
            // MUST supply application context, receiver's own context is now allowed to be used.
            OnNetworkAvailableOneTimeExecutor.execute(context.getApplicationContext(),
                    () -> refreshAllFeedsIfDownloadAllowed(context));
        }
    }

    private static boolean refreshAllFeedsIfDownloadAllowed(Context context) {
        if (NetworkUtils.isDownloadAllowed()) {
            DBTasks.refreshAllFeeds(context, null);
            return true;
        } else {
            return false;
        }
    }
}

class OnNetworkAvailableOneTimeExecutor {

    /**
     * Execute the supplied logic when network is available
     *
     * @param context
     * @param runnable the logic to be executed when network is available.
     */
    public static void execute(Context context, Runnable runnable) {
        // Hide ConnectivityManager.CONNECTIVITY_ACTION Broadcast implementation from caller
        // It might need to be changed as CONNECTIVITY_ACTION is deprecated in Android P
        // The recommended ones require API level >= 21 so the Broadcast-based implementation
        // is still needed anyway.
        OnNetworkAvailableOneTimeReceiver.execute(context, runnable);
    }
    
    private static class OnNetworkAvailableOneTimeReceiver extends BroadcastReceiver {

        private static final String TAG = "OnNetAvailOneTimeRcvr";
        
        private final Runnable runnable;
        
        private OnNetworkAvailableOneTimeReceiver(Runnable runnable) {
            this.runnable = runnable;
        }

        public static void execute(Context context, Runnable callable) {
            // OPEN: consider to avoid execute() being called repeatedly (before network becomes available)
            LogToFile.d(context, TAG, "execute() - registering network change receiver");

            BroadcastReceiver receiver = new OnNetworkAvailableOneTimeReceiver(callable);
            IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            context.registerReceiver(receiver, intentFilter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            LogToFile.d(context, TAG, "onReceive() - intent: " + intent);
            // verify intent is for network availability
            if (intent == null && !ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                LogToFile.d(context, TAG, "Not a connectivity change intent. Do nothing");
                return;
            }

            // Network not available (probably a broadcast of disconnect), ignore
            if (!NetworkUtils.networkAvailable()) {
                LogToFile.d(context, TAG, "No network available yet. Do nothing");
                return;
            }

            try {
                LogToFile.d(context, TAG, "Network available - execute supplied logic");
                runnable.run();
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in executing the logic. ", e); // No retry upon exception
            } finally {
                LogToFile.d(context, TAG, "Supplied logic executed - Now un-registering network change receiver");
                context.unregisterReceiver(this);
            }
        }
    }
}