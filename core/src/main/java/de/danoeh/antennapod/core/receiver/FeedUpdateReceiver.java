package de.danoeh.antennapod.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.Callable;

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

        // Do automatic feed update, with logic handling network stability
        Runnable doRefreshAllFeedsIfDownloadAllowed = () -> refreshAllFeedsIfDownloadAllowed(context);
        ConditionalRetryExecutor.executeWhenConditionMet(NetworkUtils::networkAvailable,
                doRefreshAllFeedsIfDownloadAllowed,
                () -> OnNetworkAvailableOneTimeExecutor.execute(context.getApplicationContext(), // MUST supply application context, receiver's own context is now allowed to be used.
                        doRefreshAllFeedsIfDownloadAllowed),
                5000,
        "Network available?",
                context);

        UserPreferences.restartUpdateAlarm(false);

    }

    private static boolean refreshAllFeedsIfDownloadAllowed(Context context) {
        if (NetworkUtils.isDownloadAllowed()) {
            LogToFile.d(context, TAG, "Automatic feed update: starting");
            DBTasks.refreshAllFeeds(context, null);
            return true;
        } else {
            LogToFile.d(context, TAG, "Blocking automatic update:  no mobile / metered network updates allowed");
            return false;
        }
    }
}

class ConditionalRetryExecutor {
    private static String TAG = "ConditionalRetryExecutor";

    /**
     * If <code>isConditionMet</code> returns true, execute <code>runnable</code>;
     * otherwise wait for <code>retryWaitMillis</code> and try again.
     * If the condition is still unmet, execute <code>fallback</code>
     *
     */
    public static void executeWhenConditionMet(Callable<Boolean> isConditionMet,
                                               Runnable runnable,
                                               Runnable fallback,
                                               long retryWaitMillis,
                                               String conditionDescription,
                                               Context context) { // Context needed temporarily for LogToFile debug
        try {
            if (isConditionMet.call()) {
                LogToFile.d(context, TAG, logMsg(conditionDescription, "Condition met."));
                runnable.run();
                return;
            } else {
                new Handler().postDelayed(() -> {
                    try {
                        if (isConditionMet.call()) {
                            LogToFile.d(context, TAG, logMsg(conditionDescription, "In retry: Condition now met."));
                            runnable.run();
                        } else {
                            LogToFile.d(context, TAG, logMsg(conditionDescription,"In retry: Condition still unmet. Execute fallback."));
                            fallback.run();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "executeWhenConditionMet() encounters unexpected exception during retry", e);
                    }
                }, retryWaitMillis);
            }
        } catch (Exception e) {
            Log.e(TAG, "executeWhenConditionMet() encounters unexpected exception", e);
            return;
        }
    }

    private static String logMsg(String logMsgPrefix, String msg) {
        return String.format("[%s] %s", logMsgPrefix, msg);
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
