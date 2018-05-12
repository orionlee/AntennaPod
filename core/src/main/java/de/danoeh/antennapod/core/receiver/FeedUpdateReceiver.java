package de.danoeh.antennapod.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
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
                () -> OnNetworkAvailableOneTimeExecutor.execute(context,
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            OnNetworkAvailableOneTimeCallback.execute(context, runnable);
        } else {
            OnNetworkAvailableOneTimeReceiver.execute(context, runnable);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static class OnNetworkAvailableOneTimeCallback extends ConnectivityManager.NetworkCallback {

        private static final String TAG = "OnNetAvailOneTimeCB";

        private final Runnable runnable;
        private final Context context; // for debug only

        private OnNetworkAvailableOneTimeCallback(Runnable runnable, Context context) {
            this.runnable = runnable;
            this.context = context;
        }

        public static void execute(Context context, Runnable runnable) {
            OnNetworkAvailableOneTimeCallback callback =
                    new OnNetworkAvailableOneTimeCallback(runnable, context);

            ConnectivityManager connManager = getConnectivityManager(context);

            connManager.registerDefaultNetworkCallback(callback); // requires (api = Build.VERSION_CODES.N)
        }

        private static ConnectivityManager getConnectivityManager(Context context) {
            return (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        private NetworkInfo getNetworkInfo(Network network) {
            return getConnectivityManager(context).getNetworkInfo(network);
        }
        
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            LogToFile.d(context, TAG, "onAvailable() network: " + getNetworkInfo(network));

            // Network still not available for some reason
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
                LogToFile.d(context, TAG, "Supplied logic executed - Now un-registering network change callback");
                getConnectivityManager(context).unregisterNetworkCallback(this);
            }

        }

        @Override
        public void onLosing(Network network, int maxMsToLive) {
            super.onLosing(network, maxMsToLive);
            LogToFile.d(context, TAG, "onLosing() network: " + getNetworkInfo(network) + " , maxMsToLive: " + maxMsToLive);
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            LogToFile.d(context, TAG, "onLost() network: " + getNetworkInfo(network));
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            LogToFile.d(context, TAG, "onUnavailable()");
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            LogToFile.d(context, TAG, "onCapabilitiesChanged(): network: " + getNetworkInfo(network) + " , capabilities: " + networkCapabilities);
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties);
            LogToFile.d(context, TAG, "onLinkPropertiesChanged() " + network + " , properties: " + linkProperties);
        }
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
            // Use application Context for receiver registration, as some context
            // (such as BroadReceiver) cannot register receivers.
            context.getApplicationContext().registerReceiver(receiver, intentFilter);
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
                context.getApplicationContext().unregisterReceiver(this);
            }
        }
    }
}
