package de.danoeh.antennapod.core.receiver;

import android.content.Context;
import android.os.Handler;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import de.danoeh.antennapod.core.util.LogToFile;

public class NetTestInternetCallReceiver extends NetTestAbstractReceiver {

    private static final String TAG = "NetTestInternetCallRcvr";

    @Override
    protected void doCaseNetworkUnavailable(Context context) {
        if (hasActiveInternetConnection(context)) {
            LogToFile.d(context, TAG, "  device is connected to internet");
        } else {
            LogToFile.d(context, TAG, "  device is not connected to internet");
        }
        logNetworkAvailable(context, "After hasActiveInternetConnection");
        new Handler().postDelayed(() -> logNetworkAvailable(context, "Delay 02s"), 2000);
    }

    private static boolean hasActiveInternetConnection(Context context) {
        try {
            HttpURLConnection urlc = (HttpURLConnection) (new URL("http://www.google.com/").openConnection());
            urlc.setRequestProperty("User-Agent", "Test");
            urlc.setRequestProperty("Connection", "close");
            urlc.setConnectTimeout(1500);
            urlc.connect();
            return true;
        } catch (IOException ioe) {
            LogToFile.d(context, TAG, "hasActiveInternetConnection() fails: " + ioe.getMessage());
            return false;
        }
    }

    @Override
    protected String tag() {
        return TAG;
    }
}
