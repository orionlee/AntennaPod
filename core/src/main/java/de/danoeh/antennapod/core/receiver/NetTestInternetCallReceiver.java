package de.danoeh.antennapod.core.receiver;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import de.danoeh.antennapod.core.util.LogToFile;

public class NetTestInternetCallReceiver extends NetTestAbstractReceiver {

    private static final String TAG = "NetTestInternetCallRcvr";

    @Override
    protected void doCaseNetworkUnavailable(Context context) {
        LogToFile.d(context, TAG, "  About to call hasActiveInternetConnection()...");
        new Handler().postDelayed(() -> new HasActiveInternetConnectionTask(context).execute(), 1000);
        new Handler().postDelayed(() -> logNetworkAvailable(context, "Delay 06s"), 6000);
    }

    private class HasActiveInternetConnectionTask extends AsyncTask<Void,Void,Void> {
        private Context context;

        public HasActiveInternetConnectionTask(Context context) {
            this.context = context;
        }

        protected Void doInBackground(Void... params) {
            if (hasActiveInternetConnection(context)) {
                LogToFile.d(context, TAG, "  device is connected to internet");
            } else {
                LogToFile.d(context, TAG, "  device is not connected to internet");
            }
            logNetworkAvailable(context, "After hasActiveInternetConnection");
            return null;
        }

    }

    private static boolean hasActiveInternetConnection(Context context) {
        try {
            HttpURLConnection urlc = (HttpURLConnection) (new URL("http://www.google.com/").openConnection());
            urlc.setRequestProperty("User-Agent", "Test");
            urlc.setRequestProperty("Connection", "close");
            urlc.setConnectTimeout(4000);
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
