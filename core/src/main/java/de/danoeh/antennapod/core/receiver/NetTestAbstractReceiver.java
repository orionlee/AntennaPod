package de.danoeh.antennapod.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import de.danoeh.antennapod.core.util.LogToFile;
import de.danoeh.antennapod.core.util.NetworkUtils;

abstract class NetTestAbstractReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        LogToFile.d(context, tag(), "Received intent.");
        if (NetworkUtils.networkAvailable()) {
            LogToFile.d(context, tag(), "Case NetworkUtils.networkAvailable() == true - No more work needed.");
        } else {
            LogToFile.d(context, tag(), "Case NetworkUtils.networkAvailable() == false - Retrying.");
            logNetworkDetails(context);
        }
    }

    protected void logNetworkAvailable(Context context, String msgPrefix) {
        LogToFile.d(context, tag(),
                String.format("  [%s]NetworkUtils.networkAvailable(): %s",
                        msgPrefix, NetworkUtils.networkAvailable()));
    }

    protected void logNetworkDetails(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        boolean probablyConnected = info != null &&
                info.isAvailable() &&
                info.getDetailedState() == NetworkInfo.DetailedState.BLOCKED &&
                info.getType() == ConnectivityManager.TYPE_WIFI;  // Might want to other non mobile type

        { // detailed logging of network status for debugging
            StringBuilder dbgMsgSB = new StringBuilder()
                    .append("  networkDetails - probablyConnected: ")
                    .append(probablyConnected)
                    .append(" , info: ")
                    .append(info);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (capabilities != null) {
                    dbgMsgSB.append(" , internet-capable: ")
                            .append(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
                    dbgMsgSB.append(" , capabilities: ")
                            .append(capabilities);
                }
            }
            LogToFile.d(context, tag(), dbgMsgSB.toString());
        }
    }

    protected abstract void doCaseNetworkUnavailable(Context context);

    protected abstract String tag();
}
